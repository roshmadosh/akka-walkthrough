package com.example.iot;

import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

/**
 * Top-level supervisor actor.
 */
public class IotSupervisor extends AbstractBehavior<Void> {

	// Factory method. Void message type are for actors that don't receive a message.
	public static Behavior<Void> create() {
		return Behaviors.setup(IotSupervisor::new);
	}

	// Private construtor that's invoked in the factory method above.
	private IotSupervisor(ActorContext<Void> context) {
		super(context);
		context.getLog().info("IoT application started");
	}

	// For logging when the actor is stopped. 
	@Override
	public Receive<Void> createReceive() {
		return newReceiveBuilder().onSignal(PostStop.class, signal -> onPostStop()).build();
	}

	private IotSupervisor onPostStop() {
		getContext().getLog().info("IoT application stopped.");
		return this;
	}

}
