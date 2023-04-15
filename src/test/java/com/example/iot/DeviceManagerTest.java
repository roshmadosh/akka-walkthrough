package com.example.iot;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.example.iot.DeviceManager.*;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;

public class DeviceManagerTest {

	@ClassRule public static final TestKitJunitResource testKit = new TestKitJunitResource();

	@Test
	public void testReplyToRegistrationRequests() {
		TestProbe<DeviceRegistered> probe = testKit.createTestProbe(DeviceRegistered.class);
		ActorRef<DeviceGroup.Command> groupActor = testKit.spawn(DeviceGroup.create("group1"));

		groupActor.tell(new RequestTrackDevice("group1", "device1", probe.getRef()));
		DeviceRegistered registered1 = probe.receiveMessage();

		// another deviceId
		groupActor.tell(new RequestTrackDevice("group1", "device2", probe.getRef()));
		DeviceRegistered registered2 = probe.receiveMessage();


		// request to register existing device
		groupActor.tell(new RequestTrackDevice("group1", "device1", probe.getRef()));
		DeviceRegistered registered3 = probe.receiveMessage();

		assertNotEquals(registered1.device, registered2.device);
		assertEquals(registered1.device, registered3.device);
	}

	@Test
	public void testListActiveDeviceGroups() {
		final String GROUP_NAME = "group1";
		final String DEVICE_NAME = "device1";
		
		TestProbe<DeviceRegistered> registeredProbe = testKit.createTestProbe(DeviceRegistered.class);
		ActorRef<DeviceManager.Command> managerActor = testKit.spawn(DeviceManager.create());

		managerActor.tell(new RequestTrackDevice(GROUP_NAME, DEVICE_NAME, registeredProbe.getRef()));
		registeredProbe.receiveMessage();

		TestProbe<ReplyDeviceList> deviceGroupListProbe = testKit.createTestProbe(ReplyDeviceList.class);
		// request device list for valid group name
		managerActor.tell(new RequestDeviceList(0L, GROUP_NAME, deviceGroupListProbe.getRef()));	
		ReplyDeviceList reply = deviceGroupListProbe.receiveMessage();

		// do the same for invalid group name
		managerActor.tell(new RequestDeviceList(1L, "bad name", deviceGroupListProbe.getRef()));	
		ReplyDeviceList badReply = deviceGroupListProbe.receiveMessage();

		assertEquals(0L, reply.requestId);
		assertEquals(Stream.of(DEVICE_NAME).collect(Collectors.toSet()), reply.ids);

		assertEquals(1L, badReply.requestId);
		assertEquals(Collections.emptySet(), badReply.ids);
	}

	@Test
	public void testListActiveDevices() {
		TestProbe<DeviceRegistered> registeredProbe = testKit.createTestProbe(DeviceRegistered.class);
		ActorRef<DeviceGroup.Command> groupActor = testKit.spawn(DeviceGroup.create("group"));

		groupActor.tell(new RequestTrackDevice("group", "device1", registeredProbe.getRef()));
		registeredProbe.receiveMessage();

		groupActor.tell(new RequestTrackDevice("group", "device2", registeredProbe.getRef()));
		registeredProbe.receiveMessage();

		TestProbe<ReplyDeviceList> deviceListProbe = testKit.createTestProbe(ReplyDeviceList.class);

		groupActor.tell(new RequestDeviceList(0L, "group", deviceListProbe.getRef()));
		ReplyDeviceList reply = deviceListProbe.receiveMessage();
		assertEquals(0L, reply.requestId);
		assertEquals(Stream.of("device1", "device2").collect(Collectors.toSet()), reply.ids);

	}



	@Test
	public void testListActiveDevicesAfterOneShutsDown() {
		TestProbe<DeviceRegistered> registeredProbe = testKit.createTestProbe(DeviceRegistered.class);
		ActorRef<DeviceGroup.Command> groupActor = testKit.spawn(DeviceGroup.create("group"));

		groupActor.tell(new RequestTrackDevice("group", "device1", registeredProbe.getRef()));
		DeviceRegistered registered1 = registeredProbe.receiveMessage();

		groupActor.tell(new RequestTrackDevice("group", "device2", registeredProbe.getRef()));
		DeviceRegistered registered2 = registeredProbe.receiveMessage();

		ActorRef<Device.Command> toShutDown = registered1.device;

		TestProbe<ReplyDeviceList> deviceListProbe = testKit.createTestProbe(ReplyDeviceList.class);

		groupActor.tell(new RequestDeviceList(0L, "group", deviceListProbe.getRef()));
		ReplyDeviceList reply = deviceListProbe.receiveMessage();
		assertEquals(0L, reply.requestId);
		assertEquals(Stream.of("device1", "device2").collect(Collectors.toSet()), reply.ids);

		toShutDown.tell(Device.Passivate.INSTANCE);
		registeredProbe.expectTerminated(toShutDown, registeredProbe.getRemainingOrDefault());

		// using awaitAssert to retry because it might take longer for the groupActor
		// to see the Terminated, that order is undefined
		registeredProbe.awaitAssert(
				() -> {
					groupActor.tell(new RequestDeviceList(1L, "group", deviceListProbe.getRef()));
					ReplyDeviceList r = deviceListProbe.receiveMessage();
					assertEquals(1L, r.requestId);
					assertEquals(Stream.of("device2").collect(Collectors.toSet()), r.ids);
					return null;
				});
	}

}
