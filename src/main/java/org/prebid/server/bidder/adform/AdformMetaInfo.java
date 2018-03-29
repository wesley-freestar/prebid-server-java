package org.prebid.server.bidder.adform;

import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.proto.response.BidderInfo;

import java.util.Collections;

/**
 * Defines Adform meta info
 */
public class AdformMetaInfo implements MetaInfo {

    private BidderInfo bidderInfo;

    public AdformMetaInfo(boolean enabled) {
        bidderInfo = BidderInfo.create(enabled, "scope.sspp@adform.com",
                Collections.singletonList("banner"), Collections.singletonList("banner"), null);
    }

    /**
     * Returns Adform bidder related meta information: maintainer email address and supported media types.
     */
    @Override
    public BidderInfo info() {
        return bidderInfo;
    }
}