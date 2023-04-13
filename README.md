# Creating an Example Temperature-Reading IoT System using Akka  

Following [this](https://doc.akka.io/docs/akka/current/typed/guide/tutorial.html) example of using Akka.  

Akka is an implementation of the [Actor model](https://doc.akka.io/docs/akka/current/typed/guide/actors-intro.html) in distributed Java/Scala applications that are supposed to overcome the difficulties faced when designing a concurrent system using the standard libraries. For example, the need for `synchronized` blocks to prevent multiple threads accessing a shared resource at the same time, and this leading to blocked method/thread execution until the shared resource becomes available.  

Rather than sharing a resource, a single "actor" is made responsible for managing the resource, which Akka calls a `Behavior`. Requests for changes to the resource is handled one at a time much like for a synchronized resource. But unlike synchronized multithreading, it avoids blocking threads by having a queue that accepts requests, or messages, to update the resource. Upon completion of the update from a single message, the actor sends a message back to the requesting actor with the data it needs and then proceeds to reading the next message in its queue.  


