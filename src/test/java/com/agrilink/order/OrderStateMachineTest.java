package com.agrilink.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agrilink.common.ApiException;
import com.agrilink.common.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OrderStateMachineTest {

    @Test
    void happyPathIsAllowedStepByStep() {
        List<OrderStatus> path = List.of(OrderStatus.PENDING, OrderStatus.ACCEPTED, OrderStatus.PAYMENT_PENDING,
                OrderStatus.PAID, OrderStatus.READY_FOR_PICKUP, OrderStatus.PICKED_UP, OrderStatus.IN_TRANSIT,
                OrderStatus.DELIVERED, OrderStatus.COMPLETED);
        for (int i = 0; i < path.size() - 1; i++) {
            assertThat(OrderStateMachine.canTransition(path.get(i), path.get(i + 1)))
                    .as("%s -> %s", path.get(i), path.get(i + 1)).isTrue();
        }
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"COMPLETED", "REJECTED", "CANCELLED", "EXPIRED"})
    void terminalStatesHaveNoExits(OrderStatus terminal) {
        assertThat(OrderStateMachine.nextStates(terminal)).isEmpty();
    }

    @Test
    void failedPaymentReturnsToAcceptedForRetry() {
        assertThat(OrderStateMachine.canTransition(OrderStatus.PAYMENT_PENDING, OrderStatus.ACCEPTED)).isTrue();
    }

    @Test
    void cannotSkipPaymentOrGoBackwards() {
        assertThat(OrderStateMachine.canTransition(OrderStatus.ACCEPTED, OrderStatus.PAID)).isFalse();
        assertThat(OrderStateMachine.canTransition(OrderStatus.DELIVERED, OrderStatus.PICKED_UP)).isFalse();
        assertThat(OrderStateMachine.canTransition(OrderStatus.PENDING, OrderStatus.COMPLETED)).isFalse();
    }

    @Test
    void cancellationIsNotPossibleAfterPickup() {
        assertThat(OrderStateMachine.canTransition(OrderStatus.PICKED_UP, OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStateMachine.canTransition(OrderStatus.IN_TRANSIT, OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStateMachine.canTransition(OrderStatus.READY_FOR_PICKUP, OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    void disputeResolvesToCompletedOrCancelledOnly() {
        assertThat(OrderStateMachine.nextStates(OrderStatus.DISPUTED))
                .containsExactlyInAnyOrder(OrderStatus.COMPLETED, OrderStatus.CANCELLED);
        assertThat(OrderStatus.DISPUTABLE).contains(OrderStatus.DELIVERED).doesNotContain(OrderStatus.PENDING);
    }

    @Test
    void illegalTransitionRaisesStructuredError() {
        assertThatThrownBy(() -> OrderStateMachine.assertTransition(OrderStatus.PENDING, OrderStatus.PAID))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
    }
}
