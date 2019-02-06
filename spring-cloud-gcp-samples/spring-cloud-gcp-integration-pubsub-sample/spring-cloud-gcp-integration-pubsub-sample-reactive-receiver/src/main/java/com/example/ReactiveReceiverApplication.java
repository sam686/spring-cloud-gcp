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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.reactivestreams.Subscription;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gcp.pubsub.core.PubSubTemplate;
import org.springframework.cloud.gcp.pubsub.integration.AckMode;
import org.springframework.cloud.gcp.pubsub.integration.inbound.PubSubInboundChannelAdapter;
import org.springframework.cloud.gcp.pubsub.support.AcknowledgeablePubsubMessage;
import org.springframework.cloud.gcp.pubsub.support.BasicAcknowledgeablePubsubMessage;
import org.springframework.cloud.gcp.pubsub.support.GcpPubSubHeaders;
import org.springframework.cloud.gcp.pubsub.support.converter.ConvertedAcknowledgeablePubsubMessage;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.FluxMessageChannel;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.handler.annotation.Header;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Spring Boot Application demonstrating receiving PubSub messages via streaming pull.
 *
 * @author João André Martins
 * @author Mike Eltsufin
 * @author Dmitry Solomakha
 * @author Chengyuan Zhao
 *
 * @since 1.1
 */
@SpringBootApplication
public class ReactiveReceiverApplication {

	private static final Log LOGGER = LogFactory.getLog(ReactiveReceiverApplication.class);
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();

	@Autowired
	PubSubTemplate template;

	public static void main(String[] args) {
		SpringApplication.run(ReactiveReceiverApplication.class, args);
	}

	// TODO: can demand be 0?

	//@Bean
	ApplicationRunner reactiveRunner() {
		return (args) -> {
			System.out.println("********** I have no idea what I am doing; pub sub template = " + template);

			//Flux<String> aflux = Flux.just("a", "b", "c", "d", "e");
			Flux<ConvertedAcknowledgeablePubsubMessage<String>> aflux = Flux.create(sink -> {
				sink.onRequest(numRequested -> {
					System.out.println("Requested " + numRequested);

					// request should be fast. Offload to another thread.
					executorService.submit(
							() ->
							{


								System.out.println("Initial demand = " + sink.requestedFromDownstream());
								long demand;
								while ( (demand = sink.requestedFromDownstream()) > 0) {
									System.out.println("Current demand: " + demand);
									List<ConvertedAcknowledgeablePubsubMessage<String>> messages
											= template.pullAndConvert("reactiveSubscription",
											demand > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)demand,
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


// infinite demand

			aflux.subscribeOn(Schedulers.newParallel("ABC", 5)).subscribe(m -> {
				System.out.println("************** I got a message: " + m.getPayload() + " (" + Thread.currentThread().getName() + ")");

				m.ack();
				try {
					int time = new Random().nextInt(1000);
					System.out.println("*** sleeping for " + time);
					Thread.sleep(time);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			});



// default demand of 1 at a time
/*
			aflux.subscribeOn(Schedulers.newParallel("ABC", 5)).subscribe(new CoreSubscriber<ConvertedAcknowledgeablePubsubMessage<String>>() {
				Subscription subscription;

				@Override
				public void onSubscribe(Subscription subscription) {
					System.out.println("************** onSubscribe!");

					this.subscription = subscription;
					this.subscription.request(5);
				}

				@Override
				public void onNext(ConvertedAcknowledgeablePubsubMessage<String> m) {
					System.out.println("************** I got a message: " + m.getPayload() + " (" + Thread.currentThread().getName() + ")");

					m.ack();
					try {
						Thread.sleep(new Random().nextInt(1000));
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					this.subscription.request(1);
				}

				@Override
				public void onError(Throwable throwable) {
					System.out.println("************** exception!");
					throwable.printStackTrace();
				}

				@Override
				public void onComplete() {
					System.out.println("************** complete!");
				}
			});
*/


		};
	}



	/*
		@Bean
	@InboundChannelAdapter(channel = "pubsubInputChannel", poller = @Poller(fixedDelay = "100"))
	public MessageSource<Object> pubsubAdapter(PubSubTemplate pubSubTemplate) {
		PubSubMessageSource messageSource = new PubSubMessageSource(pubSubTemplate,  "exampleSubscription");
		messageSource.setMaxFetchSize(5);
		messageSource.setAckMode(AckMode.MANUAL);
		messageSource.setPayloadType(String.class);
		return messageSource;
	}
	 */
/*
	@Bean
	public MessageChannel fluxMessageChannel() {
		return new FluxMessageChannel();
	}

	@ServiceActivator(inputChannel = "fluxMessageChannel")
	public void messageReceiver() {
		LOGGER.info("*********** HELLOOOOOO");

	}
*/
	/*@Bean
	public MessageChannel pubsubInputChannel() {
		return new DirectChannel();
	}

	@Bean
	public PubSubInboundChannelAdapter messageChannelAdapter(
			@Qualifier("pubsubInputChannel") MessageChannel inputChannel,
			PubSubTemplate pubSubTemplate) {
		PubSubInboundChannelAdapter adapter =
				new PubSubInboundChannelAdapter(pubSubTemplate, "exampleSubscription");
		adapter.setOutputChannel(inputChannel);
		adapter.setAckMode(AckMode.MANUAL);
		adapter.setPayloadType(String.class);
		return adapter;
	}

	@ServiceActivator(inputChannel = "pubsubInputChannel")
	public void messageReceiver(String payload,
			@Header(GcpPubSubHeaders.ORIGINAL_MESSAGE) BasicAcknowledgeablePubsubMessage message) {
		LOGGER.info("Message arrived! Payload: " + payload);
		message.ack();
	}*/

/*
	static class PubSubFlux extends Flux<AcknowledgeablePubsubMessage> {
		private List<CoreSubscriber<? super AcknowledgeablePubsubMessage>> subscribers;

		@Override
		public void subscribe(CoreSubscriber<? super AcknowledgeablePubsubMessage> coreSubscriber) {
			this.subscribers.add(coreSubscriber);

			//this.doOnNext()
			Flux.
		}


	}
	*/
}
