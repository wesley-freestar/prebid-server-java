package org.prebid.server.activity;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.request.TraceLevel;

import java.util.Arrays;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class ActivityInfrastructureTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ActivityConfiguration activityConfiguration;

    @Mock
    private Metrics metrics;

    @Test
    public void creationShouldFailOnInvalidConfiguration() {
        // when and then
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> new ActivityInfrastructure(
                        "accountId",
                        Map.of(Activity.CALL_BIDDER, activityConfiguration),
                        TraceLevel.basic,
                        metrics));
    }

    @Test
    public void isAllowedShouldReturnTrueAndUpdateMetrics() {
        // given
        given(activityConfiguration.isAllowed(argThat(arg -> arg.getComponentType().equals(ComponentType.BIDDER))))
                .willReturn(ActivityContextResult.of(true, 3));

        final ActivityInfrastructure infrastructure = activityInfrastructure(TraceLevel.verbose);

        // when
        final boolean result = infrastructure.isAllowed(Activity.CALL_BIDDER, ComponentType.BIDDER, "bidder");

        // then
        assertThat(result).isEqualTo(true);

        verify(metrics).updateRequestsActivityProcessedRulesCount(eq(3));
        verify(metrics).updateAccountActivityProcessedRulesCount(any(), eq(3));
        verify(metrics, never()).updateRequestsActivityDisallowedCount(any());
        verify(metrics, never()).updateAccountActivityDisallowedCount(any(), any());
        verify(metrics, never()).updateAdapterActivityDisallowedCount(any(), any());
    }

    @Test
    public void isAllowedShouldNotUpdateMetricsIfAllowedAndZeroProcessedRules() {
        // given
        given(activityConfiguration.isAllowed(any()))
                .willReturn(ActivityContextResult.of(true, 0));

        final ActivityInfrastructure infrastructure = activityInfrastructure(TraceLevel.basic);

        // when
        infrastructure.isAllowed(Activity.CALL_BIDDER, ComponentType.BIDDER, "bidder");

        // then
        verify(metrics, never()).updateRequestsActivityProcessedRulesCount(anyInt());
        verify(metrics, never()).updateAccountActivityProcessedRulesCount(any(), anyInt());
        verify(metrics, never()).updateRequestsActivityDisallowedCount(any());
        verify(metrics, never()).updateAccountActivityDisallowedCount(any(), any());
        verify(metrics, never()).updateAdapterActivityDisallowedCount(any(), any());
    }

    @Test
    public void isAllowedShouldUpdateExpectedMetricsIfDisallowedAndTraceLevelIsBasic() {
        // given
        given(activityConfiguration.isAllowed(any()))
                .willReturn(ActivityContextResult.of(false, 1));

        final ActivityInfrastructure infrastructure = activityInfrastructure(TraceLevel.basic);

        // when
        infrastructure.isAllowed(Activity.CALL_BIDDER, ComponentType.BIDDER, "bidder");

        // then
        verify(metrics).updateRequestsActivityProcessedRulesCount(eq(1));
        verify(metrics, never()).updateAccountActivityProcessedRulesCount(any(), anyInt());
        verify(metrics).updateRequestsActivityDisallowedCount(eq(Activity.CALL_BIDDER));
        verify(metrics, never()).updateAccountActivityDisallowedCount(any(), any());
        verify(metrics).updateAdapterActivityDisallowedCount(eq("bidder"), eq(Activity.CALL_BIDDER));
    }

    @Test
    public void isAllowedShouldUpdateExpectedMetricsIfDisallowedAndTraceLevelIsVerbose() {
        // given
        given(activityConfiguration.isAllowed(any()))
                .willReturn(ActivityContextResult.of(false, 1));

        final ActivityInfrastructure infrastructure = activityInfrastructure(TraceLevel.verbose);

        // when
        infrastructure.isAllowed(Activity.CALL_BIDDER, ComponentType.BIDDER, "bidder");

        // then
        verify(metrics).updateRequestsActivityProcessedRulesCount(eq(1));
        verify(metrics).updateAccountActivityProcessedRulesCount(eq("accountId"), eq(1));
        verify(metrics).updateRequestsActivityDisallowedCount(eq(Activity.CALL_BIDDER));
        verify(metrics).updateAccountActivityDisallowedCount(eq("accountId"), eq(Activity.CALL_BIDDER));
        verify(metrics).updateAdapterActivityDisallowedCount(eq("bidder"), eq(Activity.CALL_BIDDER));
    }

    private ActivityInfrastructure activityInfrastructure(TraceLevel traceLevel) {
        return new ActivityInfrastructure(
                "accountId",
                Arrays.stream(Activity.values())
                        .collect(Collectors.toMap(
                                UnaryOperator.identity(),
                                key -> activityConfiguration)),
                traceLevel,
                metrics);
    }
}
