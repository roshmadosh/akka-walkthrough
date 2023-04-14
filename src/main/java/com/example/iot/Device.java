package com.example.iot;

import java.util.Optional;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import scala.collection.immutable.ArraySeq.ofInt;

public class Device extends AbstractBehavior<Device.Command> {
	private final String groupId;
	private final String deviceId;
	private Optional<Double> lastTemperatureReading = Optional.empty();

	
	public interface Command {}

	// readTemp request
	public static final class ReadTemperature implements Command {
		final long requestId;
		final ActorRef<RespondTemperature> replyTo;

		public ReadTemperature(long requestId, ActorRef<RespondTemperature> replyTo) {
			this.requestId = requestId;
			this.replyTo = replyTo;
		}

	}

	// readTemp response
	public static final class RespondTemperature {
		final long requestId;
		final Optional<Double> value;

		public RespondTemperature(long requestId, Optional<Double> value) {
			this.requestId = requestId;
			this.value = value;

		}	

	}

	// writeTemp request
	public static final class RecordTemperature implements Command {
		final long requestId;
		final double value;
		final ActorRef<TemperatureRecorded> replyTo;
		
		public RecordTemperature(long requestId, double value, ActorRef<TemperatureRecorded> replyTo) {
			this.requestId = requestId;
			this.value = value;
			this.replyTo = replyTo;
		}
	}


	// writeTemp response
	public static final class TemperatureRecorded {
		final long requestId;

		public TemperatureRecorded(long requestId) {
			this.requestId = requestId;
		}
	}

	// factory method
	public static Behavior<Command> create(String groupId, String deviceId) {
		return Behaviors.setup(context -> new Device(context, groupId, deviceId));
	}	

	// private constructor
	private Device(ActorContext<Command> context, String groupId, String deviceId) {
		super(context);
		this.groupId = groupId;
		this.deviceId = deviceId;

		context.getLog().info("Device actor {}-{} started", groupId, deviceId);
	}



	@Override
	public Receive<Command> createReceive() {
		return newReceiveBuilder()
			.onMessage(ReadTemperature.class, this::onReadTemperature)
			.onMessage(RecordTemperature.class, this::onRecordTemperature)
			.onSignal(PostStop.class, signal -> onPostStop())
			.build();
	}

	private Behavior<Command> onRecordTemperature(RecordTemperature  r) {
		getContext().getLog().info("Updating temperature reading to {} with {}", r.value, r.requestId);
		lastTemperatureReading = Optional.of(r.value);
		r.replyTo.tell(new TemperatureRecorded(r.requestId));
		return this;
	}

	private Behavior<Command> onReadTemperature(ReadTemperature r) {
		r.replyTo.tell(new RespondTemperature(r.requestId, lastTemperatureReading));
		return this;	
	}

	private Device onPostStop() {
		getContext().getLog().info("Device actor {}-{} stopped", groupId, deviceId);
		return this;
	}

}
