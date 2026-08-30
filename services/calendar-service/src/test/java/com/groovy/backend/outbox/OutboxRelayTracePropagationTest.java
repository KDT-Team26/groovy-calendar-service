package com.groovy.backend.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * IR-315 / IR-312: OutboxRelay가 DB에 저장된 traceParent를 Kafka record header로 정확히 옮기는지
 * 검증한다. 실제 브로커는 안 쓰고 KafkaTemplate을 Mockito로 목킹해서, 실제로 보내려던
 * ProducerRecord의 header를 그대로 캡처해 확인한다.
 */
class OutboxRelayTracePropagationTest {

	@SuppressWarnings("unchecked")
	@Test
	void 저장된_traceParent를_카프카_레코드_header에_그대로_옮긴다() throws Exception {
		OutboxEventRepository repository = mock(OutboxEventRepository.class);
		KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
		OutboxRelay relay = new OutboxRelay(repository, kafkaTemplate);

		String traceParent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
		OutboxEvent event = OutboxEvent.builder()
			.eventId("evt-1")
			.eventType("CALENDAR_EVENT_CREATED")
			.payload("{}")
			.traceParent(traceParent)
			.build();

		when(repository.findTop50ByPublishedFalseOrderByIdAsc()).thenReturn(List.of(event));
		when(kafkaTemplate.send(any(ProducerRecord.class)))
			.thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

		relay.relay();

		ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
		verify(kafkaTemplate).send(captor.capture());

		var header = captor.getValue().headers().lastHeader("traceparent");
		assertThat(header).isNotNull();
		assertThat(new String(header.value(), StandardCharsets.UTF_8)).isEqualTo(traceParent);
	}

	@SuppressWarnings("unchecked")
	@Test
	void traceParent가_없으면_header를_안_붙인다() throws Exception {
		OutboxEventRepository repository = mock(OutboxEventRepository.class);
		KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
		OutboxRelay relay = new OutboxRelay(repository, kafkaTemplate);

		OutboxEvent event = OutboxEvent.builder()
			.eventId("evt-2")
			.eventType("CALENDAR_EVENT_CREATED")
			.payload("{}")
			.build();

		when(repository.findTop50ByPublishedFalseOrderByIdAsc()).thenReturn(List.of(event));
		when(kafkaTemplate.send(any(ProducerRecord.class)))
			.thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

		relay.relay();

		ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
		verify(kafkaTemplate).send(captor.capture());
		assertThat(captor.getValue().headers().lastHeader("traceparent")).isNull();
	}
}
