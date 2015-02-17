package org.dsa.iot.dslink;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import lombok.Getter;
import lombok.NonNull;
import lombok.val;
import org.dsa.iot.dslink.events.ChildrenUpdateEvent;
import org.dsa.iot.dslink.methods.*;
import org.dsa.iot.dslink.util.Linkable;
import org.dsa.iot.dslink.util.ResponseTracker;
import org.dsa.iot.dslink.util.StreamState;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.dsa.iot.dslink.node.NodeManager.NodeStringTuple;

/**
 * @author Samuel Grenier
 */
@Getter
public class Responder extends Linkable {

    private ResponseTracker tracker;

    public Responder(EventBus bus) {
        this(bus, new ResponseTracker());
    }

    public Responder(@NonNull EventBus bus,
                     @NonNull ResponseTracker tracker) {
        super(bus);
        this.tracker = tracker;
    }

    /**
     * @param requests The requests the other endpoint wants
     */
    @Override
    @SuppressWarnings("unchecked")
    public synchronized void parse(JsonArray requests) {
        val it = requests.iterator();
        val responses = new JsonArray();
        for (JsonObject o; it.hasNext();) {
            o = (JsonObject) it.next();

            val rid = o.getNumber("rid");
            val sMethod = o.getString("method");
            val path = o.getString("path");

            val resp = new JsonObject();
            try {
                resp.putNumber("rid", rid);
                NodeStringTuple node = null;
                if (path != null) {
                    node = getManager().getNode(path);
                }

                val method = getMethod(sMethod, rid.intValue(), node);
                val updates = method.invoke(o);
                val state = method.getState();

                if (state == null) {
                    throw new IllegalStateException("state");
                }
                if (state != StreamState.INITIALIZED) {
                    // This is a first response, default value is initialized if omitted
                    resp.putString("stream", state.jsonName);
                }
                if (state == StreamState.OPEN
                        || state == StreamState.INITIALIZED) {
                    tracker.track(rid.intValue());
                }
                if (updates != null && updates.size() > 0) {
                    resp.putElement("updates", updates);
                }
            } catch (Exception e) {
                handleInvocationError(resp, e);
            } finally {
                responses.addElement(resp);
            }
        }

        val top = new JsonObject();
        top.putElement("responses", responses);
        getClientConnector().write(top);
    }

    public void closeStream(int rid) {
        if (tracker.isTracking(rid)) {
            tracker.untrack(rid);

            val array = new JsonArray();
            {
                val obj = new JsonObject();
                obj.putNumber("rid", rid);
                obj.putString("stream", StreamState.CLOSED.jsonName);
                array.add(obj);
            }

            val resp = new JsonObject();
            resp.putArray("responses", array);
            getClientConnector().write(resp);
        }
    }

    protected Method getMethod(@NonNull String name, int rid,
                               NodeStringTuple tuple) {
        switch (name) {
            case "list":
                return new ListMethod(this, tuple.getNode(), rid);
            case "set":
                return new SetMethod(tuple.getNode(), tuple.getString());
            case "remove":
                return new RemoveMethod(tuple.getNode(), tuple.getString());
            case "invoke":
                return new InvokeMethod(tuple.getNode());
            case "subscribe":
                return new SubscribeMethod(getManager());
            case "unsubscribe":
                return new UnsubscribeMethod(getManager());
            case "close":
                return new CloseMethod(getBus(), tracker, rid);
            default:
                throw new RuntimeException("Unknown method");
        }
    }

    protected void handleInvocationError(JsonObject resp, Exception e) {
        e.printStackTrace(System.err);
        resp.putString("stream", StreamState.CLOSED.jsonName);

        val error = new JsonObject();
        error.putString("msg", e.getMessage());

        val writer = new StringWriter();
        e.printStackTrace(new PrintWriter(writer));
        error.putString("detail", writer.toString());

        resp.putElement("error", error);
    }

    @Subscribe
    protected void childrenUpdate(ChildrenUpdateEvent event) {
        if (tracker.isTracking(event.getRid())) {
            val response = new JsonObject();
            response.putNumber("rid", event.getRid());
            response.putString("stream", StreamState.OPEN.jsonName);

            val updates = new JsonArray();
            updates.addElement(ListMethod.getChildUpdate(event.getParent(), event.isRemoved()));
            response.putArray("updates", updates);

            getClientConnector().write(response);
        }
    }
}
