package com.example.iot;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.javadsl.TimerScheduler;

public class DeviceGroupQuery extends AbstractBehavior<DeviceGroupQuery.Command> {
	private final long requestId;
	private final ActorRef<DeviceManager.RespondAllTemperatures> requester;
	private final Set<String> stillWaiting;
	private Map<String, DeviceManager.TemperatureReading> repliesSoFar;


	private static enum CollectionTimeout implements Command {
		INSTANCE
	}	

	interface Command {}

	// factory method
	public static final class Builder {
		private Map<String, ActorRef<Device.Command>> deviceIdToActor;
		private long requestId;
		private ActorRef<DeviceManager.RespondAllTemperatures> requester;
		private Duration timeout;

		public Builder() {}

		public Builder deviceIdToActor(Map<String, ActorRef<Device.Command>> ref) {
			deviceIdToActor = ref;
			return this;
		}
		public Builder requestId(long value) {
			requestId = value;
			return this;
		}
		public Builder requester(ActorRef<DeviceManager.RespondAllTemperatures> ref) {
			requester = ref;
			return this;
		}
		public Builder timeout(Duration value) {
			timeout = value;
			return this;
		}

		public Behavior<Command> build() {
			return Behaviors.setup(context -> Behaviors.withTimers(timers -> new DeviceGroupQuery(this, context, timers)));
		}
	}

	private DeviceGroupQuery(Builder builder, ActorContext<Command> context, TimerScheduler<Command> timers) {
		super(context);
		this.requestId = builder.requestId;
		this.requester = builder.requester;
		this.repliesSoFar = new HashMap<>();	

		timers.startSingleTimer(CollectionTimeout.INSTANCE, builder.timeout);

		ActorRef<Device.RespondTemperature> respondTemperatureAdapter = context.messageAdapter(Device.RespondTemperature.class, 
				WrappedRespondTemperature::new);

		for (Map.Entry<String, ActorRef<Device.Command>> entry: builder.deviceIdToActor.entrySet()) {
			context.watchWith(entry.getValue(), new DeviceTerminated(entry.getKey()));
			entry.getValue().tell(new Device.ReadTemperature(0L, respondTemperatureAdapter));
		}

		stillWaiting = new HashSet<>(builder.deviceIdToActor.keySet());
	}


	static class WrappedRespondTemperature implements Command {
		final Device.RespondTemperature response;


		WrappedRespondTemperature(Device.RespondTemperature response) {
			this.response = response;
		}
	}

	private static class DeviceTerminated implements Command {
    final String deviceId;

    private DeviceTerminated(String deviceId) {
      this.deviceId = deviceId;
    }
  }

	@Override
	public Receive<Command> createReceive() {
		return newReceiveBuilder()
			.onMessage(WrappedRespondTemperature.class, this::onRespondTemperature)
			.onMessage(DeviceTerminated.class, this::onDeviceTerminated)
			.onMessage(CollectionTimeout.class, this::onCollectionTimeout)
			.build();
	}

 private Behavior<Command> onRespondTemperature(WrappedRespondTemperature r) {
		DeviceManager.TemperatureReading reading =
				r.response
						.value
						.map(v -> (DeviceManager.TemperatureReading) new DeviceManager.Temperature(v))
						.orElse(DeviceManager.TemperatureNotAvailable.INSTANCE);

		String deviceId = r.response.deviceId;
		repliesSoFar.put(deviceId, reading);
		stillWaiting.remove(deviceId);

		return respondWhenAllCollected();
	}

	private Behavior<Command> onDeviceTerminated(DeviceTerminated terminated) {
		if (stillWaiting.contains(terminated.deviceId)) {
			repliesSoFar.put(terminated.deviceId, DeviceManager.DeviceNotAvailable.INSTANCE);
			stillWaiting.remove(terminated.deviceId);
		}
		return respondWhenAllCollected();
	}

	private Behavior<Command> onCollectionTimeout(CollectionTimeout timeout) {
		for (String deviceId : stillWaiting) {
			repliesSoFar.put(deviceId, DeviceManager.DeviceTimedOut.INSTANCE);
		}
		stillWaiting.clear();
		return respondWhenAllCollected();
	}

	private Behavior<Command> respondWhenAllCollected() {
		if (stillWaiting.isEmpty()) {
			requester.tell(new DeviceManager.RespondAllTemperatures(requestId, repliesSoFar));
			return Behaviors.stopped();
		}

		return this;
	}
}
