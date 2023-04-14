package com.example.iot;

import static org.junit.Assert.*;

import org.junit.ClassRule;
import org.junit.Test;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;

public class DeviceManagerTest {

	@ClassRule public static final TestKitJunitResource testKit = new TestKitJunitResource();


	@Test
	public void testReplyToRegistrationRequests() {
		TestProbe<DeviceManager.DeviceRegistered> probe = testKit.createTestProbe(DeviceManager.DeviceRegistered.class);
		ActorRef<DeviceGroup.Command> groupActor = testKit.spawn(DeviceGroup.create("group1"));

		groupActor.tell(new DeviceManager.RequestTrackDevice("group1", "device1", probe.getRef()));
		DeviceManager.DeviceRegistered registered1 = probe.receiveMessage();

		// another deviceId
		groupActor.tell(new DeviceManager.RequestTrackDevice("group1", "device2", probe.getRef()));
		DeviceManager.DeviceRegistered registered2 = probe.receiveMessage();


		// request to register existing device
		groupActor.tell(new DeviceManager.RequestTrackDevice("group1", "device1", probe.getRef()));
		DeviceManager.DeviceRegistered registered3 = probe.receiveMessage();

		assertNotEquals(registered1.device, registered2.device);
		assertEquals(registered1.device, registered3.device);
	}
}
