package com.example.iot;

import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;

public class DeviceManager extends AbstractBehavior<DeviceManager.Command> {

	private final Map<String, ActorRef<DeviceGroup.Command>> groupIdToActor = new HashMap<>();

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

	// --[MESSAGES]-- //

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

	public static class ReplyDeviceList {
		final long requestId;
		final Set<String> ids;

		public ReplyDeviceList(long requestId, Set<String> ids) {
			this.requestId = requestId;	
			this.ids = ids;
		}

	}

	public static final class DeviceGroupTerminated implements Command {
		public final String groupId;

		public DeviceGroupTerminated(String groupId) {
			this.groupId = groupId;
		}
	}

	public static final class RequestAllTemperatures implements DeviceGroup.Command, DeviceGroupQuery.Command, Command {
		final long requestId;
		final String groupId;
		final ActorRef<RespondAllTemperatures> replyTo;

		public RequestAllTemperatures(long requestId, String groupId, ActorRef<RespondAllTemperatures> replyTo) {
			this.requestId = requestId;
			this.groupId = groupId;
			this.replyTo = replyTo;
		}
	}
	public static final class RespondAllTemperatures {
		final long requestId;
		final Map<String, TemperatureReading> temperatures;

		public RespondAllTemperatures(long requestId, Map<String, TemperatureReading> temperatures) {
			this.requestId = requestId;
			this.temperatures = temperatures;
		}
	}

	public interface TemperatureReading {}
	

	public static final class Temperature implements TemperatureReading {
		public final double value;

		public Temperature(double value) {
			this.value = value;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			Temperature that = (Temperature) o;

			return Double.compare(that.value, value) == 0;
		}

		@Override
		public int hashCode() {
			long temp = Double.doubleToLongBits(value);
			return (int) (temp ^ (temp >>> 32));
		}

		@Override
		public String toString() {
			return "Temperature{" + "value=" + value + '}';
		}
	}

	public enum TemperatureNotAvailable implements TemperatureReading {
		INSTANCE
	}

	public enum DeviceNotAvailable implements TemperatureReading {
		INSTANCE
	}
	public enum DeviceTimedOut implements TemperatureReading {
		INSTANCE
	}

	// ---

	@Override
	public Receive<Command> createReceive() {
		return newReceiveBuilder()
			.onMessage(RequestTrackDevice.class, this::onTrackDevice)
			.onMessage(RequestDeviceList.class, this::onDeviceList)
			.onMessage(DeviceGroupTerminated.class, this::onDeviceGroupTerminated)
			.onSignal(PostStop.class, signal -> onPostStop())
			.build();
	}

	// --[HANDLERS]-- //

	private DeviceManager onTrackDevice(RequestTrackDevice trackMessage) {
		ActorRef<DeviceGroup.Command> groupRef = groupIdToActor.get(trackMessage.groupId);

		// if device group isn't already tracked
		if (groupRef == null) {
			getContext().getLog().info("Creating device group {}", trackMessage.groupId);
			groupRef = getContext().spawn(DeviceGroup.create(trackMessage.groupId), "group-" + trackMessage.groupId);
			getContext().watchWith(groupRef, new DeviceGroupTerminated(trackMessage.groupId));
		}

		// forward track message so device is tracked
		groupRef.tell(trackMessage);
		
		groupIdToActor.put(trackMessage.groupId, groupRef);

		return this;
	}

	private DeviceManager onDeviceList(RequestDeviceList r) {
		ActorRef<DeviceGroup.Command> ref = groupIdToActor.get(r.groupId);

		// if group doesn't exist, send reply with empty set
		if (ref == null) {
			getContext().getLog().warn("Request for device list of non-existent device group {}", r.groupId);
			r.replyTo.tell(new ReplyDeviceList(r.requestId, Collections.emptySet()));	
		} else { // otherwise get device list from group
			ref.tell(r);
		}

		return this;
	}

	private DeviceManager onDeviceGroupTerminated(DeviceGroupTerminated r) {
		getContext().getLog().info("Device group {} terminated", r.groupId);
		groupIdToActor.remove(r.groupId);
		return this;	
	}

	private DeviceManager onPostStop() {
		getContext().getLog().info("Device manager stopped.");
		return this;
	}
}
