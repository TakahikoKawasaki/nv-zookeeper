nv-zookeeper
============

Overview
--------

ZooKeeper Utility Library.


License
-------

Apache License, Version 2.0


Maven
-----

```xml
<dependency>
    <groupId>com.neovisionaries</groupId>
    <artifactId>nv-zookeeper</artifactId>
    <version>1.2</version>
</dependency>
```


Source Download
---------------

    git clone http://github.com/TakahikoKawasaki/nv-zookeeper.git


JavaDoc
-------

[JavaDoc of nv-zookeeper](http://TakahikoKawasaki.github.io/nv-zookeeper/)



Examples
--------

#### Leader Election

```java
// Prepare a ZooKeeper instance.
ZooKeeper zooKeeper = ...

// Prepare a Listener implementation.
LeaderElection.Listener listener = new LeaderElection.Leader() {
    @Override
    public void onWin(LeaderElection election) {
        System.out.println("I'm the leader.");
    }

    @Override
    public void onLose(LeaderElection election) {
        System.out.println("Someone else is the leader.");
    }

    @Override
    public void onVacant(LeaderElection election) {
        System.out.println("The leader resigned. An election will be conducted again.");
    }

    @Override
    public void onFinish(LeaderElection election) {
        System.out.println("The callback chain ended. Not run for election any more.");
    }

    @Override
    public void onStateChanged(LeaderElection election, State oldState, State newState) {
        System.out.format("The state was changed from %s to %s.\n", oldState, newState);
    }
};

// Conduct a leader election.
new LeaderElection()
    .setZooKeeper(zooKeeper)
    .setListener(listener)
    .start();

// Same as above.
new LeaderElection()
    .setZooKeeper(zooKeeper)
    .setListener(listener)
    .setPath("/leader")
    .setId(
        String.valueOf(Math.abs(new Random().nextLong()))
    )
    .setAclList(ZooDefs.Ids.OPEN_ACL_UNSAFE)
    .start();
```

This implementation repeats to join a leader election, i.e. continues to schedule
a callback (and a watcher as necessary), until it detects either of the following.

1. The given `ZooKeeper` instance reports `AUTH_FAILED` or `CLOSED`.
2. This instance is marked as `shouldStop` by `finish()`.

`LeaderElection.Adapter` is an empty implementation of `LeaderElection.Listener`.
You may find it useful when you are interested in only some of the callback methods.
For example, if you are interested in only `onStateChanged()`, using `Adapter`
will make your code shorter like below.

```java
// Conduct a leader election.
new LeaderElection()
    .setZooKeeper(zooKeeper)
    .setListener(new Adapter() {
        @Override
        public void onStateChanged(LeaderElection election, State oldState, State newState) {
            System.out.format("The state was changed from %s to %s.\n", oldState, newState);
        }
    })
    .start();
```


See Also
--------

* [Apache ZooKeeper](http://zookeeper.apache.org/)
* [Apache ZooKeeper JavaDoc](http://zookeeper.apache.org/doc/current/api/index.html)


Author
------

Takahiko Kawasaki, Neo Visionaries Inc.
