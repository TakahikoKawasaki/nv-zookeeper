/*
 * Copyright (C) 2015 Neo Visionaries Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.neovisionaries.zookeeper;


import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Random;
import org.apache.zookeeper.AsyncCallback.DataCallback;
import org.apache.zookeeper.AsyncCallback.StatCallback;
import org.apache.zookeeper.AsyncCallback.StringCallback;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;


/**
 * An implementation of a "leader election" algorithm using ZooKeeper.
 *
 * <pre style="border: 1px solid black; padding: 0.5em; margin: 1em;">
 * <span style="color: darkgreen;">// Prepare a ZooKeeper instance.</span>
 * ZooKeeper zooKeeper = ...
 *
 * <span style="color: darkgreen;">// Prepare a Listener implementation.</span>
 * LeaderElection.{@link Listener} listener = <span style="color: purple; font-weight: bold;">new</span> LeaderElection.Listener() {
 *     <span style="color: gray;">&#x40;Override</span>
 *     <span style="color: purple; font-weight: bold;">public void</span> {@link Listener#onWin(LeaderElection) onWin}(LeaderElection election) {
 *         System.out.println(<span style="color: mediumblue;">"I'm the leader."</span>);
 *     }
 *
 *     <span style="color: gray;">&#x40;Override</span>
 *     <span style="color: purple; font-weight: bold;">public void</span> {@link Listener#onLose(LeaderElection) onLose}(LeaderElection election) {
 *         System.out.println(<span style="color: mediumblue;">"Someone else is the leader."</span>);
 *     }
 *
 *     <span style="color: gray;">&#x40;Override</span>
 *     <span style="color: purple; font-weight: bold;">public void</span> {@link Listener#onVacant(LeaderElection) onVacant}(LeaderElection election) {
 *         System.out.println(<span style="color: mediumblue;">"The leader resigned. An election will be conducted again."</span>);
 *     }
 *
 *     <span style="color: gray;">&#x40;Override</span>
 *     <span style="color: purple; font-weight: bold;">public void</span> {@link Listener#onFinish(LeaderElection) onFinish}(LeaderElection election) {
 *         System.out.println(<span style="color: mediumblue;">"The callback chain ended. Not run for election any more."</span>);
 *     }
 *
 *     <span style="color: gray;">&#x40;Override</span>
 *     <span style="color: purple; font-weight: bold;">public void</span> {@link Listener#onStateChanged(LeaderElection, State, State)
 *     onStateChanged}(LeaderElection election, {@link State} oldState, {@link State} newState) {
 *         System.out.format(<span style="color: mediumblue;">"The state was changed from %s to %s.\n"</span>, oldState, newState);
 *     }
 * };
 *
 * <span style="color: darkgreen;">// Conduct a leader election.</span>
 * <span style="color: purple; font-weight: bold;">new</span> LeaderElection()
 *     .{@link #setZooKeeper(ZooKeeper) setZooKeeper}(zooKeeper)
 *     .{@link #setListener(Listener) setListener}(listener)
 *     .{@link #start()};
 *
 * <span style="color: darkgreen;">// Same as above.</span>
 * <span style="color: purple; font-weight: bold;">new</span> LeaderElection()
 *     .{@link #setZooKeeper(ZooKeeper) setZooKeeper}(zooKeeper)
 *     .{@link #setListener(Listener) setListener}(listener)
 *     .{@link #setPath(String) setPath}(<span style="color: mediumblue;">"/leader"</span>)
 *     .{@link #setId(String) setId}(
 *         String.valueOf(Math.abs(<span style="color: purple; font-weight: bold;">new</span> Random().nextLong()))
 *     )
 *     .{@link #setAclList(List) setAclList}(ZooDefs.Ids.OPEN_ACL_UNSAFE)
 *     .{@link #start()};
 * </pre>
 *
 * <p>
 * This implementation repeats to join a leader election, i.e.
 * continues to schedule a callback (and a watcher as necessary),
 * unless it detects either of the following.
 * </p>
 * <blockquote>
 * <ol>
 * <li>The given {@link ZooKeeper} instance reports {@link
 * ZooKeeper.States#AUTH_FAILED AUTH_FAILED} or {@link
 * ZooKeeper.States#CLOSED CLOSED}.
 * <li>This instance is marked as 'shouldStop' by {@link #finish()}.
 * </ol>
 * </blockquote>
 *
 * <p>
 * {@link Adapter} is an empty implementation of {@link Listener}. You may
 * find it useful when you are interested in only some of the callback methods.
 * For example, if you are interested in only {@link
 * Listener#onStateChanged(LeaderElection, State, State) onStateChanged()},
 * using {@code Adapter} will make your code shorter like below.
 * </p>
 *
 * <pre style="border: 1px solid black; padding: 0.5em; margin: 1em;">
 * <span style="color: darkgreen;">// Conduct a leader election.</span>
 * <span style="color: purple; font-weight: bold;">new</span> LeaderElection()
 *     .{@link #setZooKeeper(ZooKeeper) setZooKeeper}(zooKeeper)
 *     .{@link #setListener(Listener) setListener}(<span style="color: purple; font-weight: bold;">new</span> LeaderElection.{@link Adapter}() {
 *         <span style="color: gray;">&#x40;Override</span>
 *         <span style="color: purple; font-weight: bold;">public void</span> {@link Listener#onStateChanged(LeaderElection, State, State)
 *         onStateChanged}(LeaderElection election, {@link State} oldState, {@link State} newState) {
 *             System.out.format(<span style="color: mediumblue;">"The state was changed from %s to %s.\n"</span>, oldState, newState);
 *         }
 *     })
 *     .{@link #start()};
 * </pre>
 *
 * @author Takahiko Kawasaki
 */
public class LeaderElection
{
    /**
     * The listener to receive leader election events.
     */
    public interface Listener
    {
        /**
         * Called when this candidate won a leader election.
         * This callback method is called after {@link
         * #onStateChanged(LeaderElection, State, State) onStateChanged()}.
         *
         * @param election
         *         The {@link LeaderElection} instance which this
         *         listener is associated with.
         */
        void onWin(LeaderElection election);

        /**
         * Called when it is detected that another candidate is the leader.
         * This callback method is called after {@link
         * #onStateChanged(LeaderElection, State, State) onStateChanged()}.
         *
         * @param election
         *         The {@link LeaderElection} instance which this
         *         listener is associated with.
         */
        void onLose(LeaderElection election);

        /**
         * Called when it is detected that a leader does not exist.
         * This callback method is called after {@link
         * #onStateChanged(LeaderElection, State, State) onStateChanged()}.
         *
         * @param election
         *         The {@link LeaderElection} instance which this
         *         listener is associated with.
         */
        void onVacant(LeaderElection election);

        /**
         * Called when it is detected that callback should not be called
         * any more. Note that this method may not be called so soon and
         * may not be called at all. This callback method is called, if
         * called, after {@link #onStateChanged(LeaderElection, State, State)
         * onStateChanged()}.
         *
         * @param election
         *         The {@link LeaderElection} instance which this
         *         listener is associated with.
         */
        void onFinish(LeaderElection election);

        /**
         * Called when the state of the leader election changed.
         * This callback method is called before other
         * <code>on<i>Xxx</i></code> methods
         * ({@link #onWin(LeaderElection) onWin()},
         * {@link #onLose(LeaderElection) onLose()}, {@link
         * #onVacant(LeaderElection) onVacant()} and {@link
         * #onFinish(LeaderElection) onFinish()}).
         *
         * @param election
         *         The {@link LeaderElection} instance which this
         *         listener is associated with.
         *
         * @param oldState
         *         The previous state before the change.
         *
         * @param newState
         *         The new state after the change.
         *
         * @since 1.1
         */
        void onStateChanged(LeaderElection election, State oldState, State newState);
    }


    /**
     * An empty implementation of {@link Listener}.
     *
     * @since 1.1
     */
    public static class Adapter implements Listener
    {
        @Override
        public void onWin(LeaderElection election)
        {
        }

        @Override
        public void onLose(LeaderElection election)
        {
        }

        @Override
        public void onVacant(LeaderElection election)
        {
        }

        @Override
        public void onFinish(LeaderElection election)
        {
        }

        @Override
        public void onStateChanged(LeaderElection election, State oldState, State newState)
        {
        }
    }


    /**
     * Leader election state.
     *
     * <pre style="margin: 1em;">
     *           State Transition
     *
     *                     +----------------+
     *                     |  +----------+  |
     *                     |  |  LEADER  |  |
     *                     |  +----------+  |
     *                     |        A       |
     *                     |        |       |
     *                     |        V       |
     * +---------+         |  +----------+  |
     * | CREATED |----------->| ELECTING |  |
     * +---------+         |  +----------+  |
     *      |              |        A       |
     *      |              |        |       |
     *      V              |        V       |
     * +---------+         |  +----------+  |
     * |  DONE   |<--------|  | FOLLOWER |  |
     * +---------+         |  +----------+  |
     *                     +----------------+
     * </pre>
     *
     * <p>
     * The initial state of {@link LeaderElection} is {@link #CREATED}.
     * </p>
     *
     * <p>
     * {@link LeaderElection#start()} method can be called only when the state
     * is {@code CREATED}. When {@code start()} method is called when the state
     * is not {@code CREATED}, an {@code IllegalStateException} is thrown.
     * </p>
     *
     * <p>
     * In the implementation of {@code start()} method, the state is changed
     * to {@link #ELECTING} before the method returns. This means that
     * {@link Listener#onStateChanged(LeaderElection, State, State)
     * Listener.onStateChanged()} is called before {@code start()} method is
     * returned. In some unusual cases, however, the state is changed to
     * {@link #DONE}. This happens when the given {@link ZooKeeper} instance
     * reports {@link org.apache.zookeeper.ZooKeeper.States#AUTH_FAILED
     * AUTH_FAILED} or {@link org.apache.zookeeper.ZooKeeper.States#CLOSED
     * CLOSED}, or when you have called {@link LeaderElection#finish()} before
     * calling {@code start()} method.
     * </p>
     *
     * <p>
     * As a result of leader election, the state is changed to either {@link
     * #LEADER} or {@link #FOLLOWER}. The state {@code LEADER} means that
     * the {@code LeaderElection} instance has won the leader election and
     * now is the leader. The state {@code FOLLOWER} means that the {@code
     * LeaderElection} instance has lost the leader election and now is a
     * follower.
     * </p>
     *
     * <p>
     * The state may be changed back to {@code ELECTING} from {@code LEADER}
     * or {@code FOLLOWER}. This happens when it is detected that the znode
     * for leader election has been deleted. In this case, another new
     * leader election will be executed without delay, and as a result, the
     * state will be changed to either {@code LEADER} or {@code FOLLOWER}
     * again.
     * </p>
     *
     * <p>
     * The implementation of {@code LeaderElection} triggers a ZooKeeper
     * <a href="http://zookeeper.apache.org/doc/current/api/org/apache/zookeeper/AsyncCallback.html"
     * >callback</a> as necessary. At the timing, the state of the given
     * {@link ZooKeeper} instance is checked by calling {@link
     * ZooKeeper#getState()} method. If the ZooKeeper's state is either
     * {@code AUTH_FAILED} or {@code CLOSED}, a ZooKeeper callback is not
     * triggered, and instead, the state of {@code LeaderElection} is
     * changed to {@code DONE}.
     * </p>
     *
     * @since 1.1
     */
    public enum State
    {
        /**
         * The initial state of a {@link LeaderElection} instance.
         */
        CREATED,

        /**
         * The {@link LeaderElection} instance is now the leader.
         */
        LEADER,

        /**
         * The {@link LeaderElection} instance is now a follower.
         */
        FOLLOWER,

        /**
         * Leader election is now being conducted.
         */
        ELECTING,

        /**
         * The {@link LeaderElection} instance has stopped working
         * and will not join leader election any further.
         */
        DONE
    }


    private static final String DEFAULT_PATH = "/leader";
    private static final List<ACL> DEFAULT_ACL_LIST = ZooDefs.Ids.OPEN_ACL_UNSAFE;


    private ZooKeeper mZooKeeper;
    private String mPath;
    private String mId;
    private byte[] mIdBytes;
    private List<ACL> mAclList;
    private StringCallback mRunForLeaderCallback = new RunForLeaderCallback();
    private DataCallback mCheckLeaderCallback = new CheckLeaderCallback();
    private Watcher mTrackLeaderWatcher = new TrackLeaderWatcher();
    private StatCallback mTrackLeaderCallback = new TrackLeaderCallback();
    private Listener mListener;
    private boolean mShouldFinish;
    private State mState = State.CREATED;


    public LeaderElection()
    {
    }


    public LeaderElection(ZooKeeper zooKeeper)
    {
        mZooKeeper = zooKeeper;
    }


    /**
     * Get the {@link ZooKeeper} instance used for leader election.
     *
     * @return
     *         The {@link ZooKeeper} instance used for leader election.
     */
    public ZooKeeper getZooKeeper()
    {
        return mZooKeeper;
    }


    /**
     * Set the {@link ZooKeeper} instance used for leader election.
     *
     * <p>
     * If no {@link ZooKeeper} instance is set when {@link #start()} is called,
     * an {@code IllegalStateException} is thrown.
     * </p>
     *
     * @param zooKeeper
     *         The {@link ZooKeeper} instance used for leader election.
     *
     * @return
     *         {@code this} object.
     */
    public LeaderElection setZooKeeper(ZooKeeper zooKeeper)
    {
        mZooKeeper = zooKeeper;

        return this;
    }


    /**
     * Get the znode path used for leader election.
     *
     * @return
     *         The znode path used for leader election.
     */
    public String getPath()
    {
        return mPath;
    }


    /**
     * Set the znode path used for leader election.
     *
     * <p>
     * If no znode path is set when {@link #start()} is called,
     * the default value, {@code "/leader"}, is used.
     * </p>
     *
     * @param path
     *         The znode path used for leader election.
     *
     * @return
     *         {@code this} object.
     */
    public LeaderElection setPath(String path)
    {
        mPath = path;

        return this;
    }


    /**
     * Get the ID that represents this candidate in leader election.
     *
     * @return
     *         The ID that represents this candidate in leader election.
     */
    public String getId()
    {
        return mId;
    }


    /**
     * Set the ID that represents this candidate in leader election.
     * The value must be different from other candidates' IDs.
     *
     * <p>
     * If no ID is set when {@link #start()} is called, a random ID
     * is generated.
     * </p>
     *
     * @param id
     *         The ID that represents this candidate in leader election.
     *
     * @return
     *         {@code this} object.
     */
    public LeaderElection setId(String id)
    {
        mId = id;

        return this;
    }


    /**
     * Get the ACL list used for creation of the znode for leader election.
     *
     * @return
     *         The ACL list used for creation of the znode for leader election.
     */
    public List<ACL> getAclList()
    {
        return mAclList;
    }


    /**
     * Set the ACL list used for creation of the znode for leader election.
     *
     * <p>
     * If no ACL list is set when {@link #start()} is called, the default list,
     * {@code ZooDefs.Ids.}{@link ZooDefs.Ids.OPEN_ACL_UNSAFE OPEN_ACL_UNSAFE},
     * is used.
     * </p>
     *
     * @param list
     *         The ACL list used for creation of the znode for leader election.
     *
     * @return
     *         {@code this} object.
     */
    public LeaderElection setAclList(List<ACL> list)
    {
        mAclList = list;

        return this;
    }


    /**
     * Get the listener for leader election events.
     *
     * @return
     *         The listener for leader election events.
     */
    public Listener getListener()
    {
        return mListener;
    }


    /**
     * Set the listener for leader election events.
     *
     * @param listener
     *         The listener for leader election events.
     *
     * @return
     *         {@code this} object.
     */
    public LeaderElection setListener(Listener listener)
    {
        mListener = listener;

        return this;
    }


    /**
     * Start leader election.
     *
     * <p>
     * This implementation repeats to join a leader election, i.e.
     * continues to schedule a callback (and a watcher as necessary),
     * unless it detects either of the following.
     * </p>
     *
     * <blockquote>
     * <ol>
     * <li>The given {@link ZooKeeper} instance reports {@link
     * ZooKeeper.States#AUTH_FAILED AUTH_FAILED} or {@link
     * ZooKeeper.States#CLOSED CLOSED}.
     * <li>This instance is marked as 'shouldStop' by {@link #finish()}.
     * </ol>
     * </blockquote>
     *
     * @return
     *         {@code this} object.
     *
     * @throws IllegalStateException
     *         <ul>
     *           <li>No {@link ZooKeeper} instance is set.
     *           <li>The current state is not {@link State#CREATED CREATED}.
     *         </ul>
     */
    public LeaderElection start()
    {
        setup();

        synchronized (this)
        {
            if (mState != State.CREATED)
            {
                throw new IllegalStateException(
                    "start() can be called only when the state is CREATED. " +
                    "The current state is " + mState + ".");
            }

            if (runForLeader())
            {
                changeState(State.ELECTING);
            }
        }

        return this;
    }


    /**
     * Mark as 'shouldFinish' not to schedule ZooKeeper callbacks
     * any further. After calling {@code finish()}, this {@code
     * LeaderElection} instance never joins a leader election.
     *
     * <p>
     * Note that calling this method does not remove an existing
     * {@link Watcher} which is watching the znode for leader
     * election.
     * </p>
     *
     * @return
     *         {@code this} object.
     */
    public LeaderElection finish()
    {
        synchronized (this)
        {
            mShouldFinish = true;
        }

        return this;
    }


    /**
     * Get the current {@link State state}.
     *
     * @return
     *         The current state.
     *
     * @since 1.1
     */
    public State getState()
    {
        synchronized (this)
        {
            return mState;
        }
    }


    private void setup()
    {
        // If a ZooKeeper instance is not set.
        if (mZooKeeper == null)
        {
            // A ZooKeeper instance must be set before start().
            throw new IllegalStateException("A ZooKeeper instance must be set.");
        }

        // If a path is not set.
        if (mPath == null)
        {
            // Use the default path.
            mPath = DEFAULT_PATH;
        }

        // If an ID is not set.
        if (mId == null)
        {
            // Generate a random ID.
            mId = String.valueOf(Math.abs(new Random().nextLong()));
        }

        // Convert the ID into a byte array.
        mIdBytes = getBytes(mId);

        // If an ACL list is not set.
        if (mAclList == null)
        {
            // Use the default list.
            mAclList = DEFAULT_ACL_LIST;
        }
    }


    private boolean shouldFinish()
    {
        synchronized (this)
        {
            if (mShouldFinish)
            {
                return true;
            }
        }

        switch (mZooKeeper.getState())
        {
            case AUTH_FAILED:
            case CLOSED:
                return true;

            default:
                return false;
        }
    }


    private boolean finishIfAppropriate()
    {
        boolean shouldFinish = shouldFinish();

        if (shouldFinish)
        {
            changeState(State.DONE);
            callOnFinish();
        }

        return shouldFinish;
    }


    private void changeState(State state)
    {
        State oldState;
        State newState;

        synchronized (this)
        {
            oldState = mState;
            newState = state;

            mState = state;

            callOnStateChanged(oldState, newState);
        }
    }


    private boolean runForLeader()
    {
        if (finishIfAppropriate())
        {
            // Return without calling ZooKeeper.create().
            // This means that the callback chain is terminated here.
            return false;
        }

        mZooKeeper.create(mPath, mIdBytes, mAclList,
            CreateMode.EPHEMERAL, mRunForLeaderCallback, null);

        return true;
    }


    private void checkLeader()
    {
        if (finishIfAppropriate())
        {
            // Stop the call chain.
            return;
        }

        mZooKeeper.getData(mPath, false, mCheckLeaderCallback, null);
    }


    private void trackLeader()
    {
        if (finishIfAppropriate())
        {
            // Stop the call chain.
            return;
        }

        mZooKeeper.exists(mPath, mTrackLeaderWatcher, mTrackLeaderCallback, null);
    }


    private class RunForLeaderCallback implements StringCallback
    {
        @Override
        public void processResult(int rc, String path, Object ctx, String name)
        {
            switch (Code.get(rc))
            {
                case OK:
                    // I'm the leader. Track myself.
                    changeState(State.LEADER);
                    callOnWin();
                    trackLeader();
                    return;

                case NODEEXISTS:
                    // I'm not the leader but a follower. Track the leader.
                    changeState(State.FOLLOWER);
                    callOnLose();
                    trackLeader();
                    return;

                default:
                    // Check who is the leader.
                    checkLeader();
                    return;
            }
        }
    }


    private class CheckLeaderCallback implements DataCallback
    {
        @Override
        public void processResult(int rc, String path, Object ctx, byte[] data, Stat stat)
        {
            switch (Code.get(rc))
            {
                case OK:
                    // The leader znode exists. Check the content of the leader znode.
                    processLeaderNode(data);
                    return;

                case NONODE:
                    // Nobody is the leader. Run for the leader.
                    changeState(State.ELECTING);
                    callOnVacant();
                    runForLeader();
                    return;

                default:
                    // Retry to check who is the leader.
                    checkLeader();
                    return;
            }
        }


        private void processLeaderNode(byte[] data)
        {
            // Interpret the content of the leader znode as the leader's ID.
            String id = newString(data);

            // If the ID equals to my own.
            if (mId.equals(id))
            {
                // I'm the leader.
                changeState(State.LEADER);
                callOnWin();
            }
            else
            {
                // I'm not the leader but a follower.
                changeState(State.FOLLOWER);
                callOnLose();
            }

            // Track the leader.
            trackLeader();
        }
    }


    private class TrackLeaderWatcher implements Watcher
    {
        @Override
        public void process(WatchedEvent event)
        {
            if (event.getType() == EventType.NodeDeleted)
            {
                // The leader resigned.
                changeState(State.ELECTING);
                callOnVacant();

                // Run for the leader.
                runForLeader();
            }
        }
    }


    private class TrackLeaderCallback implements StatCallback
    {
        @Override
        public void processResult(int rc, String path, Object ctx, Stat stat)
        {
            switch (Code.get(rc))
            {
                case OK:
                    return;

                case NONODE:
                    // Nobody is the leader. Run for the leader.
                    changeState(State.ELECTING);
                    callOnVacant();
                    runForLeader();
                    return;

                default:
                    // Keep tracking the leader.
                    trackLeader();
                    return;
            }
        }
    }


    private void callOnWin()
    {
        if (mListener == null)
        {
            return;
        }

        try
        {
            mListener.onWin(this);
        }
        catch (RuntimeException e)
        {
            // Ignore.
        }
    }


    private void callOnLose()
    {
        if (mListener == null)
        {
            return;
        }

        try
        {
            mListener.onLose(this);
        }
        catch (RuntimeException e)
        {
            // Ignore.
        }
    }


    private void callOnVacant()
    {
        if (mListener == null)
        {
            return;
        }

        try
        {
            mListener.onVacant(this);
        }
        catch (RuntimeException e)
        {
            // Ignore.
        }
    }


    private void callOnFinish()
    {
        if (mListener == null)
        {
            return;
        }

        try
        {
            mListener.onFinish(this);
        }
        catch (RuntimeException e)
        {
            // Ignore.
        }
    }


    private void callOnStateChanged(State oldState, State newState)
    {
        if (mListener == null)
        {
            return;
        }

        try
        {
            mListener.onStateChanged(this, oldState, newState);
        }
        catch (RuntimeException e)
        {
            // Ignore.
        }
    }


    private byte[] getBytes(String string)
    {
        if (string == null)
        {
            return null;
        }

        try
        {
            // Convert the given string into a byte array. Note that
            // "string.getBytes(StandardCharsets.UTF_8)" is not used
            // intentionally so that this library can work on Java 1.5.
            return string.getBytes("UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            // This never happens.
            return null;
        }
    }


    private String newString(byte[] bytes)
    {
        if (bytes == null)
        {
            return null;
        }

        try
        {
            // Convert the byte array into a string. Note that
            // "new String(data, StandardCharsets.UTF_8)" is not
            // used intentionally so that this library can work
            // on Java 1.5.
            return new String(bytes, "UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            // This never happens.
            return null;
        }
    }
}
