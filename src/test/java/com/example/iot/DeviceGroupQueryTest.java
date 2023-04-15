package com.example.iot;

import static org.junit.Assert.assertEquals;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.ClassRule;
import org.junit.Test;

import com.example.iot.DeviceManager.RespondAllTemperatures;
import com.example.iot.DeviceManager.Temperature;
import com.example.iot.DeviceManager.TemperatureReading;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;

public class DeviceGroupQueryTest {
	@ClassRule public static final TestKitJunitResource testKit = new TestKitJunitResource();

	@Test
	public void testReturnTemperatureValueForWorkingDevices() {
		TestProbe<DeviceManager.RespondAllTemperatures> requester =
				testKit.createTestProbe(RespondAllTemperatures.class);
		TestProbe<Device.Command> device1 = testKit.createTestProbe(Device.Command.class);
		TestProbe<Device.Command> device2 = testKit.createTestProbe(Device.Command.class);

		Map<String, ActorRef<Device.Command>> deviceIdToActor = new HashMap<>();
		deviceIdToActor.put("device1", device1.getRef());
		deviceIdToActor.put("device2", device2.getRef());

		ActorRef<DeviceGroupQuery.Command> queryActor =
				testKit.spawn(
						new DeviceGroupQuery.Builder()
							.deviceIdToActor(deviceIdToActor)
							.requestId(1L)
							.requester(requester.getRef())
							.timeout(Duration.ofSeconds(3))
							.build());

		device1.expectMessageClass(Device.ReadTemperature.class);
		device2.expectMessageClass(Device.ReadTemperature.class);

		queryActor.tell(
				new DeviceGroupQuery.WrappedRespondTemperature(
						new Device.RespondTemperature(0L, "device1", Optional.of(1.0))));

		queryActor.tell(
				new DeviceGroupQuery.WrappedRespondTemperature(
						new Device.RespondTemperature(0L, "device2", Optional.of(2.0))));

		RespondAllTemperatures response = requester.receiveMessage();
		assertEquals(1L, response.requestId);

		Map<String, TemperatureReading> expectedTemperatures = new HashMap<>();
		expectedTemperatures.put("device1", new Temperature(1.0));
		expectedTemperatures.put("device2", new Temperature(2.0));

		assertEquals(expectedTemperatures, response.temperatures);
	}
}
