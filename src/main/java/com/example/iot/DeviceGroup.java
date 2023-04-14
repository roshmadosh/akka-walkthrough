package com.example.iot;

import java.util.HashMap;
import java.util.Map;


import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class DeviceGroup extends AbstractBehavior<DeviceGroup.Command> {
	private final String groupId;
	private final Map<String, ActorRef<Device.Command>> deviceIdToActor = new HashMap<>();

	public interface Command {}


	// factory method
	public static Behavior<DeviceGroup.Command> create(String groupId) {
		return Behaviors.setup(context -> new DeviceGroup(context, groupId));
	}

	// private constructor
	private DeviceGroup(ActorContext<Command> context, String groupId) {
		super(context);
		this.groupId = groupId;
		context.getLog().info("Device Group {} started.", groupId);
	}

	private class DeviceTerminated implements Command {
    public final ActorRef<Device.Command> device;
    public final String groupId;
    public final String deviceId;

    DeviceTerminated(ActorRef<Device.Command> device, String groupId, String deviceId) {
      this.device = device;
      this.groupId = groupId;
      this.deviceId = deviceId;
    }
  }

	@Override
	public Receive<Command> createReceive() {
		return newReceiveBuilder()
			.onMessage(DeviceManager.RequestTrackDevice.class, this::onTrackDevice)
			.onMessage(DeviceManager.RequestDeviceList.class, r -> r.groupId.equals(groupId), this::onDeviceList)
			.onMessage(DeviceTerminated.class, this::onTerminated)
			.build();
	}

	private DeviceGroup onTrackDevice(DeviceManager.RequestTrackDevice trackMessage) {
		if (this.groupId.equals(trackMessage.groupId)) {
			ActorRef<Device.Command> deviceRef = deviceIdToActor.get(trackMessage.deviceId);
			if (deviceRef == null) {
				getContext().getLog().info("Creating actor for device {}", trackMessage.deviceId);
				deviceRef = getContext().spawn(Device.create(trackMessage.groupId, trackMessage.deviceId), "device-" + trackMessage.deviceId);

				getContext().watchWith(deviceRef, new DeviceTerminated(deviceRef, groupId, trackMessage.deviceId));
			}


			deviceIdToActor.put(trackMessage.deviceId, deviceRef);
			trackMessage.replyTo.tell(new DeviceManager.DeviceRegistered(deviceRef));
		} else {
			getContext()
				.getLog()
				.warn("Ignoring TrackDevice request for {}. This actor is responsible for {}.", trackMessage.groupId, this.groupId);
		}
		
		return this;
	}

	private DeviceGroup onDeviceList(DeviceManager.RequestDeviceList request) {
		request.replyTo.tell(new DeviceManager.ReplyDeviceList(request.requestId, deviceIdToActor.keySet()));
		return this;
	}

	private DeviceGroup onTerminated(DeviceTerminated device) {
		getContext().getLog().info("Device actor {} has been terminated", device.deviceId);
		deviceIdToActor.remove(device.deviceId);
		return this;
	}

}
