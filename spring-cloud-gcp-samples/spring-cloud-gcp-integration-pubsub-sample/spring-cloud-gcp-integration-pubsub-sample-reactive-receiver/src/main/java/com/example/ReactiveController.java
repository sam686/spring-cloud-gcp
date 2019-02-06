/*
 * Copyright 2017-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gcp.pubsub.core.PubSubTemplate;
import org.springframework.cloud.gcp.pubsub.support.converter.ConvertedAcknowledgeablePubsubMessage;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Elena Felder
 * @since 1.2
 */
@RestController
public class ReactiveController {

	private final ExecutorService executorService = Executors.newSingleThreadExecutor();

	@Autowired
	PubSubTemplate template;

	@GetMapping(value = "/getmessages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<? super String> getPatientAlerts() {
		Flux<ConvertedAcknowledgeablePubsubMessage> flux = Flux.create(sink -> {
			sink.onRequest(numRequested -> {
				System.out.println("Requested " + numRequested);

				// request should be fast. Offload to another thread.
				executorService.submit(
						() ->
						{


							System.out.println("Initial demand = " + sink.requestedFromDownstream());
							long demand;
							while ((demand = sink.requestedFromDownstream()) > 0) {
								System.out.println("Current demand: " + demand);
								List<ConvertedAcknowledgeablePubsubMessage<String>> messages
										= template.pullAndConvert("reactiveSubscription",
										demand > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) demand,
										false,
										String.class);

								System.out.println("Got " + messages.size() + " messages");
								messages.forEach(m -> {
									System.out.println("Sending message to subscriber: " + m.getPayload());
									sink.next(m);
								});
							}


						});
			});
		});
		return flux.map(message -> message.getPayload());

	}
}
