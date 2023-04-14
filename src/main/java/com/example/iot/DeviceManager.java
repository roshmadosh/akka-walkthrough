package com.example.iot;

import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import scala.collection.immutable.ArraySeq.ofInt;

public class DeviceManager extends AbstractBehavior<DeviceManager.Command> {

	public interface Command {}

	// factory method
	public static Behavior<Command> create() {
		return Behaviors.setup(context -> new DeviceManager(context));
	}

	// private constuctor
	private DeviceManager(ActorContext<Command> context) {
		super(context);
		context.getLog().info("Device manager started.");
	}

	public static final class RequestTrackDevice implements DeviceManager.Command, DeviceGroup.Command {
		public final String groupId;
		public final String deviceId;
		public final ActorRef<DeviceRegistered> replyTo;

		public RequestTrackDevice(String groupId, String deviceId, ActorRef<DeviceRegistered> replyTo) {
			this.groupId = groupId;
			this.deviceId = deviceId;
			this.replyTo = replyTo;
		}
	}	

	public static final class DeviceRegistered {
		public final ActorRef<Device.Command> device;

		public DeviceRegistered(ActorRef<Device.Command> device) {
			this.device = device;
		}
	}

	public Receive<Command> createReceive() {
		return newReceiveBuilder().onSignal(PostStop.class, signal -> onPostStop()).build();
	}

	private DeviceManager onPostStop() {
		getContext().getLog().info("Device manager stopped.");
		return this;
	}
}
