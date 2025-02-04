package org.prebid.server.bidder.rhythmone;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.rhythmone.ExtImpRhythmone;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class RhythmoneBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpRhythmone>> RHYTHMONE_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public RhythmoneBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();

        String composedUrl = null;
        final List<Imp> modifiedImps = new ArrayList<>();
        for (Imp imp : bidRequest.getImp()) {
            try {
                final ExtImpRhythmone parsedImpExt = parseAndValidateImpExt(imp);

                if (composedUrl == null) {
                    composedUrl = "%s/%s/0/%s?z=%s&s2s=true".formatted(
                            endpointUrl,
                            parsedImpExt.getPlacementId(),
                            parsedImpExt.getPath(),
                            parsedImpExt.getZone());
                }
                final ExtImpRhythmone modifiedImpExt = parsedImpExt.toBuilder().s2s(true).build();
                final Imp modifiedImp = imp.toBuilder().ext(impExtToObjectNode(modifiedImpExt)).build();
                modifiedImps.add(modifiedImp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (composedUrl == null) {
            return Result.of(Collections.emptyList(), errors);
        }

        final BidRequest outgoingRequest = bidRequest.toBuilder().imp(modifiedImps).build();

        return Result.of(Collections.singletonList(BidderUtil.defaultRequest(outgoingRequest, composedUrl, mapper)),
                errors);
    }

    private ExtImpRhythmone parseAndValidateImpExt(Imp imp) {
        final ExtImpRhythmone impExt;
        try {
            impExt = mapper.mapper().convertValue(imp.getExt(), RHYTHMONE_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(
                    "ext data not provided in imp id=%s. Abort all Request".formatted(imp.getId()), e);
        }

        if (StringUtils.isBlank(impExt.getPlacementId()) || StringUtils.isBlank(impExt.getZone())
                || StringUtils.isBlank(impExt.getPath())) {
            throw new PreBidException(
                    "placementId | zone | path not provided in imp id=%s. Abort all Request".formatted(imp.getId()));
        }
        return impExt;
    }

    private ObjectNode impExtToObjectNode(ExtImpRhythmone extImpRhythmone) {
        final ObjectNode impExt;
        try {
            impExt = mapper.mapper().valueToTree(ExtPrebid.of(null, extImpRhythmone));
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Failed to create imp.ext with error: " + e.getMessage());
        }
        return impExt;
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(httpCall.getRequest().getPayload(), bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())
                ? Collections.emptyList()
                : bidsFromResponse(bidRequest, bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), bidRequest.getImp()), bidResponse.getCur()))
                .toList();
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                } else if (imp.getVideo() != null) {
                    return BidType.video;
                }
            }
        }
        return BidType.banner;
    }
}
