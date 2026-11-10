package com.digitalbank.transfer.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.digitalbank.transfer.domain.exception.TransferNotFoundException;
import com.digitalbank.transfer.domain.model.Transfer;
import com.digitalbank.transfer.infrastructure.persistence.TransferViewRepository;

@ExtendWith(MockitoExtension.class)
class TransferQueryServiceTest {

    private static final UUID FROM = UUID.randomUUID();
    private static final UUID TO = UUID.randomUUID();

    @Mock
    TransferViewRepository views;

    TransferQueryService service;
    TransferView view;

    @BeforeEach
    void setUp() {
        service = new TransferQueryService(views);
        view = TransferView.of(Transfer.request(FROM, TO, new BigDecimal("10.00"), null, Instant.now()));
        when(views.findById(view.getId())).thenReturn(Optional.of(view));
    }

    @Test
    void senderAndReceiverCanSeeTheTransfer() {
        assertThat(service.findVisibleTo(view.getId(), FROM)).isSameAs(view);
        assertThat(service.findVisibleTo(view.getId(), TO)).isSameAs(view);
    }

    @Test
    void thirdPartyGetsNotFoundInsteadOfForbidden() {
        assertThatThrownBy(() -> service.findVisibleTo(view.getId(), UUID.randomUUID()))
                .isInstanceOf(TransferNotFoundException.class);
    }
}
