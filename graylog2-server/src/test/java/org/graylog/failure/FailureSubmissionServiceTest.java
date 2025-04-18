/*
 * Copyright (C) 2020 Graylog, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Server Side Public License, version 1,
 * as published by MongoDB, Inc.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * Server Side Public License for more details.
 *
 * You should have received a copy of the Server Side Public License
 * along with this program. If not, see
 * <http://www.mongodb.com/licensing/server-side-public-license>.
 */
package org.graylog.failure;

import org.graylog2.indexer.messages.IndexingError;
import org.graylog2.inputs.diagnosis.InputDiagnosisMetrics;
import org.graylog2.plugin.Message;
import org.graylog2.plugin.inputs.failure.InputProcessingException;
import org.graylog2.plugin.journal.RawMessage;
import org.graylog2.shared.bindings.providers.ObjectMapperProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.graylog2.indexer.messages.IndexingError.Type.MappingError;
import static org.graylog2.indexer.messages.IndexingError.Type.Unknown;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FailureSubmissionServiceTest {

    @Mock
    private FailureSubmissionQueue failureSubmissionQueue;
    @Mock
    private FailureHandlingConfiguration failureHandlingConfiguration;
    @Mock
    private ObjectMapperProvider objectMapperProvider;
    @Mock
    private InputDiagnosisMetrics inputDiagnosisMetrics;
    @InjectMocks
    private FailureSubmissionService underTest;

    @Captor
    private ArgumentCaptor<FailureBatch> failureBatchCaptor;

    @Test
    void submitIndexingErrors_allIndexingErrorsTransformedAndSubmittedToFailureQueue() throws Exception {
        // given
        final Message msg1 = mock(Message.class);
        String messageId1 = "msg-1";
        when(msg1.getMessageId()).thenReturn(messageId1);
        when(msg1.supportsFailureHandling()).thenReturn(true);
        final Message msg2 = mock(Message.class);
        String messageId2 = "msg-2";
        when(msg2.getMessageId()).thenReturn(messageId2);
        when(msg2.supportsFailureHandling()).thenReturn(true);

        final List<IndexingError> indexingErrors = List.of(
                IndexingError.create(msg1, "index-1", MappingError, "Error"),
                IndexingError.create(msg2, "index-2", Unknown, "Error2")
        );

        // when
        underTest.submitIndexingErrors(indexingErrors);

        // then
        verify(failureSubmissionQueue, times(1)).submitBlocking(failureBatchCaptor.capture());

        assertThat(failureBatchCaptor.getValue()).satisfies(fb -> {
            assertThat(fb.containsIndexingFailures()).isTrue();
            assertThat(fb.size()).isEqualTo(2);

            assertThat(fb.getFailures().get(0)).satisfies(indexingFailure -> {
                assertThat(indexingFailure.failureType()).isEqualTo(FailureType.INDEXING);
                assertThat(indexingFailure.failureCause().label()).isEqualTo("MappingError");
                assertThat(indexingFailure.message()).isEqualTo("Failed to index message with id 'msg-1' targeting 'index-1'");
                assertThat(indexingFailure.failureDetails()).isEqualTo("Error");
                assertThat(indexingFailure.failureTimestamp()).isNotNull();
                assertThat(indexingFailure.messageId()).isEqualTo(messageId1);
                assertThat(indexingFailure.targetIndex()).isEqualTo("index-1");
                assertThat(indexingFailure.requiresAcknowledgement()).isFalse();
            });

            assertThat(fb.getFailures().get(1)).satisfies(indexingFailure -> {
                assertThat(indexingFailure.failureType()).isEqualTo(FailureType.INDEXING);
                assertThat(indexingFailure.failureCause().label()).isEqualTo("UNKNOWN");
                assertThat(indexingFailure.message()).isEqualTo("Failed to index message with id 'msg-2' targeting 'index-2'");
                assertThat(indexingFailure.failureDetails()).isEqualTo("Error2");
                assertThat(indexingFailure.failureTimestamp()).isNotNull();
                assertThat(indexingFailure.messageId()).isEqualTo(messageId2);
                assertThat(indexingFailure.targetIndex()).isEqualTo("index-2");
                assertThat(indexingFailure.requiresAcknowledgement()).isFalse();
            });
        });
    }

    @Test
    void submitIndexingErrors_messageNotSupportingFailureHandlingNotSubmittedToQueue() {
        // given
        final Message msg1 = mock(Message.class);
        when(msg1.supportsFailureHandling()).thenReturn(false);
        final Message msg2 = mock(Message.class);
        when(msg2.supportsFailureHandling()).thenReturn(false);

        final List<IndexingError> indexingErrors = List.of(
                IndexingError.create(msg1, "index-1", MappingError, "Error"),
                IndexingError.create(msg2, "index-2", Unknown, "Error2")
        );

        // when
        underTest.submitIndexingErrors(indexingErrors);

        // then
        verifyNoInteractions(failureSubmissionQueue);
    }


    @Test
    void submitProcessingErrors_allProcessingErrorsSubmittedToQueueAndMessageNotFilteredOut_ifSubmissionEnabledAndDuplicatesAreKept() throws Exception {
        // given
        final Message msg = mock(Message.class);
        String messageId = "msg-x";
        when(msg.getMessageId()).thenReturn(messageId);
        when(msg.supportsFailureHandling()).thenReturn(true);

        when(msg.processingErrors()).thenReturn(List.of(
                new Message.ProcessingError(() -> "Cause 1", "Message 1", "Details 1"),
                new Message.ProcessingError(() -> "Cause 2", "Message 2", "Details 2")
        ));

        when(failureHandlingConfiguration.submitProcessingFailures()).thenReturn(true);
        when(failureHandlingConfiguration.keepFailedMessageDuplicate()).thenReturn(true);

        // when
        final boolean notFilterOut = underTest.submitProcessingErrors(msg);

        // then

        assertThat(notFilterOut).isTrue();

        verify(failureSubmissionQueue, times(2)).submitBlocking(failureBatchCaptor.capture());

        assertThat(failureBatchCaptor.getAllValues().get(0)).satisfies(fb -> {
            assertThat(fb.containsProcessingFailures()).isTrue();
            assertThat(fb.size()).isEqualTo(1);

            assertThat(fb.getFailures().get(0)).satisfies(processingFailure -> {
                assertThat(processingFailure.failureType()).isEqualTo(FailureType.PROCESSING);
                assertThat(processingFailure.failureCause().label()).isEqualTo("Cause 1");
                assertThat(processingFailure.message()).isEqualTo("Failed to process message with id 'msg-x': Message 1");
                assertThat(processingFailure.failureDetails()).isEqualTo("Details 1");
                assertThat(processingFailure.failureTimestamp()).isNotNull();
                assertThat(processingFailure.messageId()).isEqualTo(messageId);
                assertThat(processingFailure.targetIndex()).isNull();
                assertThat(processingFailure.requiresAcknowledgement()).isFalse();
            });
        });

        assertThat(failureBatchCaptor.getAllValues().get(1)).satisfies(fb -> {
            assertThat(fb.containsProcessingFailures()).isTrue();
            assertThat(fb.size()).isEqualTo(1);

            assertThat(fb.getFailures().get(0)).satisfies(processingFailure -> {
                assertThat(processingFailure.failureType()).isEqualTo(FailureType.PROCESSING);
                assertThat(processingFailure.failureCause().label()).isEqualTo("Cause 2");
                assertThat(processingFailure.message()).isEqualTo("Failed to process message with id 'msg-x': Message 2");
                assertThat(processingFailure.failureDetails()).isEqualTo("Details 2");
                assertThat(processingFailure.failureTimestamp()).isNotNull();
                assertThat(processingFailure.messageId()).isEqualTo(messageId);
                assertThat(processingFailure.targetIndex()).isNull();
                assertThat(processingFailure.requiresAcknowledgement()).isFalse();
            });
        });
    }

    @Test
    void submitProcessingErrors_nothingSubmittedAndMessageNotFilteredOut_ifSubmissionEnabledAndDuplicatesAreKeptAndMessageDoesntSupportFailureHandling() {
        // given
        final Message msg = mock(Message.class);
        when(msg.supportsFailureHandling()).thenReturn(false);

        when(msg.processingErrors()).thenReturn(List.of(
                new Message.ProcessingError(() -> "Cause 1", "Message 1", "Details 1"),
                new Message.ProcessingError(() -> "Cause 2", "Message 2", "Details 2")
        ));

        lenient().when(failureHandlingConfiguration.submitProcessingFailures()).thenReturn(true);
        lenient().when(failureHandlingConfiguration.keepFailedMessageDuplicate()).thenReturn(true);

        // when
        final boolean notFilterOut = underTest.submitProcessingErrors(msg);

        // then
        assertThat(notFilterOut).isTrue();

        verifyNoInteractions(failureSubmissionQueue);
    }


    @Test
    void submitProcessingErrors_nothingSubmittedAndMessageNotFilteredOut_ifSubmissionDisabledAndDuplicatesAreKept() {
        // given
        final Message msg = mock(Message.class);
        when(msg.supportsFailureHandling()).thenReturn(true);

        when(msg.processingErrors()).thenReturn(List.of(
                new Message.ProcessingError(() -> "Cause 1", "Message 1", "Details 1"),
                new Message.ProcessingError(() -> "Cause 2", "Message 2", "Details 2")
        ));

        when(failureHandlingConfiguration.submitProcessingFailures()).thenReturn(false);
        lenient().when(failureHandlingConfiguration.keepFailedMessageDuplicate()).thenReturn(true);

        // when
        final boolean notFilterOut = underTest.submitProcessingErrors(msg);

        // then

        assertThat(notFilterOut).isTrue();

        verifyNoInteractions(failureSubmissionQueue);
    }

    @Test
    void submitProcessingErrors_nothingSubmittedAndMessageNotFilteredOut_ifSubmissionDisabledAndDuplicatesAreNotKept() {
        // given
        final Message msg = mock(Message.class);

        when(msg.processingErrors()).thenReturn(List.of(
                new Message.ProcessingError(() -> "Cause 1", "Message 1", "Details 1"),
                new Message.ProcessingError(() -> "Cause 2", "Message 2", "Details 2")
        ));

        lenient().when(failureHandlingConfiguration.submitProcessingFailures()).thenReturn(false);
        lenient().when(failureHandlingConfiguration.keepFailedMessageDuplicate()).thenReturn(false);

        // when
        final boolean notFilterOut = underTest.submitProcessingErrors(msg);

        // then

        assertThat(notFilterOut).isTrue();

        verifyNoInteractions(failureSubmissionQueue);
    }

    @Test
    void submitProcessingErrors_nothingSubmittedAndMessageNotFilteredOut_ifMessageHasNoErrors() {
        // given
        final Message msg = mock(Message.class);

        // when
        final boolean notFilterOut = underTest.submitProcessingErrors(msg);

        // then
        assertThat(notFilterOut).isTrue();

        verifyNoInteractions(failureSubmissionQueue);
    }


    @Test
    void submitProcessingErrors_processingErrorSubmittedToQueueAndMessageFilteredOut_ifSubmissionEnabledAndDuplicatesAreNotKept() throws Exception {
        // given
        final Message msg = mock(Message.class);
        String messageId = "msg-x";
        when(msg.getMessageId()).thenReturn(messageId);
        when(msg.supportsFailureHandling()).thenReturn(true);
        when(msg.processingErrors()).thenReturn(List.of(
                new Message.ProcessingError(() -> "Cause", "Message", "Details")
        ));

        when(failureHandlingConfiguration.submitProcessingFailures()).thenReturn(true);
        when(failureHandlingConfiguration.keepFailedMessageDuplicate()).thenReturn(false);

        // when
        final boolean notFilterOut = underTest.submitProcessingErrors(msg);

        // then

        assertThat(notFilterOut).isFalse();

        verify(msg).setFilterOut(true);

        verify(failureSubmissionQueue, times(1)).submitBlocking(failureBatchCaptor.capture());

        assertThat(failureBatchCaptor.getValue()).satisfies(fb -> {
            assertThat(fb.containsProcessingFailures()).isTrue();
            assertThat(fb.size()).isEqualTo(1);

            assertThat(fb.getFailures().get(0)).satisfies(processingFailure -> {
                assertThat(processingFailure.failureType()).isEqualTo(FailureType.PROCESSING);
                assertThat(processingFailure.failureCause().label()).isEqualTo("Cause");
                assertThat(processingFailure.message()).isEqualTo("Failed to process message with id 'msg-x': Message");
                assertThat(processingFailure.failureDetails()).isEqualTo("Details");
                assertThat(processingFailure.failureTimestamp()).isNotNull();
                assertThat(processingFailure.messageId()).isEqualTo(messageId);
                assertThat(processingFailure.targetIndex()).isNull();
                assertThat(processingFailure.requiresAcknowledgement()).isTrue();
            });
        });
    }

    @Test
    void submitUnknownProcessingError_unknownProcessingErrorSubmittedToQueue() throws Exception {
        // given
        final Message msg = mock(Message.class);
        when(msg.supportsFailureHandling()).thenReturn(true);

        when(failureHandlingConfiguration.submitProcessingFailures()).thenReturn(true);
        when(failureHandlingConfiguration.keepFailedMessageDuplicate()).thenReturn(true);

        // when
        final boolean notFilterOut = underTest.submitUnknownProcessingError(msg, "Details of the unknown error!");

        // then

        assertThat(notFilterOut).isTrue();

        verify(failureSubmissionQueue, times(1)).submitBlocking(failureBatchCaptor.capture());

        assertThat(failureBatchCaptor.getValue()).satisfies(fb -> {
            assertThat(fb.containsProcessingFailures()).isTrue();
            assertThat(fb.size()).isEqualTo(1);

            assertThat(fb.getFailures().get(0)).satisfies(processingFailure -> {
                assertThat(processingFailure.failureType()).isEqualTo(FailureType.PROCESSING);
                assertThat(processingFailure.failureCause().label()).isEqualTo("UNKNOWN");
                assertThat(processingFailure.message()).isEqualTo("Failed to process message with id 'UNKNOWN': Encountered an unrecognizable processing error");
                assertThat(processingFailure.failureDetails()).isEqualTo("Details of the unknown error!");
                assertThat(processingFailure.failureTimestamp()).isNotNull();
                assertThat(processingFailure.targetIndex()).isNull();
                assertThat(processingFailure.requiresAcknowledgement()).isFalse();
            });
        });
    }

    @Test
    @DisplayName("Ensure Message#getId() is used as a fallback for Message#getMessageId()")
    void submitProcessingErrorWithIdButnoMessageId() throws Exception {
        // given
        final Message msg = mock(Message.class);
        when(msg.getId()).thenReturn("msg-uuid");
        when(msg.supportsFailureHandling()).thenReturn(true);

        when(failureHandlingConfiguration.submitProcessingFailures()).thenReturn(true);
        when(failureHandlingConfiguration.keepFailedMessageDuplicate()).thenReturn(true);

        // when
        underTest.submitUnknownProcessingError(msg, "Details of the unknown error!");

        // then
        verify(failureSubmissionQueue, times(1)).submitBlocking(failureBatchCaptor.capture());

        assertThat(failureBatchCaptor.getValue()).satisfies(fb -> {
            assertThat(fb.containsProcessingFailures()).isTrue();
            assertThat(fb.size()).isEqualTo(1);

            assertThat(fb.getFailures().get(0)).satisfies(processingFailure ->
                    assertThat(processingFailure.message()).isEqualTo("Failed to process message with id 'msg-uuid': Encountered an unrecognizable processing error"));
        });
    }

    @Test
    void submitInputError_inputErrorTransformedAndSubmittedToFailureQueue() throws Exception {
        RawMessage rawMessage = new RawMessage(new byte[]{});
        InputProcessingException inputException = InputProcessingException.create("error1", rawMessage);

        underTest.submitInputFailure(inputException, "1234");

        verify(failureSubmissionQueue, times(1)).submitBlocking(failureBatchCaptor.capture());
        assertThat(failureBatchCaptor.getValue()).satisfies(fb -> {
            assertThat(fb.containsInputFailures()).isTrue();
            assertThat(fb.size()).isEqualTo(1);

            assertThat(fb.getFailures().get(0)).satisfies(indexingFailure -> {
                assertThat(indexingFailure.failureType()).isEqualTo(FailureType.INPUT);
                assertThat(indexingFailure.failureCause().label()).isEqualTo("InputParseError");
                assertThat(indexingFailure.message()).isEqualTo("Failed to process message with id '%s' from input with id '1234': %s"
                        .formatted(rawMessage.getId().toString(), inputException.getMessage()));
                assertThat(indexingFailure.failureDetails()).isEqualTo(inputException.getMessage());
                assertThat(indexingFailure.failureTimestamp()).isNotNull();
                assertThat(indexingFailure.messageTimestamp()).isEqualTo(rawMessage.getTimestamp());
                assertThat(indexingFailure.messageId()).isEqualTo(rawMessage.getId().toString());
                assertThat(indexingFailure.requiresAcknowledgement()).isFalse();
            });
        });
    }

    @Test
    void submitInputError_rootCauseMessageIsUsedForFailureDetails() throws Exception {
        InputProcessingException inputException = InputProcessingException.create("error1",
                new IllegalArgumentException("rootCauseMessage"), new RawMessage(new byte[]{}));

        underTest.submitInputFailure(inputException, "1234");

        verify(failureSubmissionQueue, times(1)).submitBlocking(failureBatchCaptor.capture());
        assertThat(failureBatchCaptor.getValue()).satisfies(fb ->
                assertThat(fb.getFailures().get(0)).satisfies(indexingFailure ->
                        assertThat(indexingFailure.failureDetails()).isEqualTo("IllegalArgumentException: rootCauseMessage")));
    }
}
