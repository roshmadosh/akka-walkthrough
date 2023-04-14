package com.example.iot;

import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.util.Set;

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

	public static final class RequestDeviceList implements DeviceManager.Command, DeviceGroup.Command {
		final long requestId;
		final String groupId;
		final ActorRef<ReplyDeviceList> replyTo;


		public RequestDeviceList(long requestId, String groupId, ActorRef<ReplyDeviceList> replyTo) {
			this.requestId = requestId;
			this.groupId = groupId;
			this.replyTo = replyTo;
		}
	}	

	public static final class ReplyDeviceList {
		final long requestId;
		final Set<String> ids;

		public ReplyDeviceList(long requestId, Set<String> ids) {
			this.requestId = requestId;	
			this.ids = ids;
		}

	}


	@Override
	public Receive<Command> createReceive() {
		return newReceiveBuilder().onSignal(PostStop.class, signal -> onPostStop()).build();
	}

	// stop handler
	private DeviceManager onPostStop() {
		getContext().getLog().info("Device manager stopped.");
		return this;
	}
}
