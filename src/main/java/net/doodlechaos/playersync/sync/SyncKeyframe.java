package net.doodlechaos.playersync.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializer;
import net.doodlechaos.playersync.input.containers.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.lang.reflect.Type;
import java.util.List;

public class SyncKeyframe {

    public final long frame;
    public final float tickDelta;
    public final Vec3 playerPos;
    public float playerYaw;
    public float playerPitch;
    public final Vec3 playerVel;

    public final Vec3 camPos;
    public final Quaternionf camRot;

    public final List<MyInputEvent> recordedInputEvents;
    public final List<String> cmds;

    public SyncKeyframe(long frame, float tickDelta, Vec3 playerPos, float playerYaw, float playerPitch, Vec3 playerVel,
                        Vec3 camPos, Quaternionf camRot, List<MyInputEvent> inputEvents, List<String> cmds) {
        this.frame = frame;
        this.tickDelta = tickDelta;
        this.playerPos = playerPos;
        this.playerYaw = playerYaw;
        this.playerPitch = playerPitch;
        this.playerVel = playerVel;
        this.camPos = camPos;
        this.camRot = camRot;
        this.recordedInputEvents = inputEvents;
        this.cmds = cmds;
    }

    public void addCommand(String command) {
        cmds.add(command);
    }

    /**
     * Serializes this SyncKeyframe into a JSON string using Gson.
     */
    public String toJson() {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Vec3.class, new Vec3Adapter())
                .registerTypeAdapter(Quaternionf.class, new QuaternionfAdapter())
                .create();
        return gson.toJson(this);
    }

    /**
     * Deserializes a JSON string into a SyncKeyframe instance using Gson.
     */
    public static SyncKeyframe fromJson(String json) {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Vec3.class, new Vec3Adapter())
                .registerTypeAdapter(Quaternionf.class, new QuaternionfAdapter())
                .create();
        return gson.fromJson(json, SyncKeyframe.class);
    }

    // Custom Gson adapter for Vec3.
    public static class Vec3Adapter implements JsonSerializer<Vec3>, JsonDeserializer<Vec3> {
        @Override
        public JsonElement serialize(Vec3 src, Type typeOfSrc, com.google.gson.JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("x", src.x);
            obj.addProperty("y", src.y);
            obj.addProperty("z", src.z);
            return obj;
        }

        @Override
        public Vec3 deserialize(JsonElement json, Type typeOfT, com.google.gson.JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            double x = obj.get("x").getAsDouble();
            double y = obj.get("y").getAsDouble();
            double z = obj.get("z").getAsDouble();
            return new Vec3(x, y, z);
        }
    }

    // Custom Gson adapter for Quaternionf.
    public static class QuaternionfAdapter implements JsonSerializer<Quaternionf>, JsonDeserializer<Quaternionf> {
        @Override
        public JsonElement serialize(Quaternionf src, Type typeOfSrc, com.google.gson.JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("x", src.x);
            obj.addProperty("y", src.y);
            obj.addProperty("z", src.z);
            obj.addProperty("w", src.w);
            return obj;
        }

        @Override
        public Quaternionf deserialize(JsonElement json, Type typeOfT, com.google.gson.JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            float x = obj.get("x").getAsFloat();
            float y = obj.get("y").getAsFloat();
            float z = obj.get("z").getAsFloat();
            float w = obj.get("w").getAsFloat();
            return new Quaternionf(x, y, z, w);
        }
    }

    // Custom Gson adapter for MyInputEvent.
    public static class MyInputEventAdapter implements JsonSerializer<MyInputEvent>, JsonDeserializer<MyInputEvent> {
        @Override
        public JsonElement serialize(MyInputEvent src, Type typeOfSrc, com.google.gson.JsonSerializationContext context) {
            // Serialize the concrete subclass and then add a "type" field.
            JsonElement jsonElem = context.serialize(src, src.getClass());
            JsonObject jsonObj = jsonElem.getAsJsonObject();
            jsonObj.addProperty("type", src.getClass().getSimpleName());
            return jsonObj;
        }

        @Override
        public MyInputEvent deserialize(JsonElement json, Type typeOfT, com.google.gson.JsonDeserializationContext context) throws com.google.gson.JsonParseException {
            JsonObject jsonObj = json.getAsJsonObject();
            if (!jsonObj.has("type")) {
                throw new JsonParseException("Missing type field in MyInputEvent JSON");
            }
            String type = jsonObj.get("type").getAsString();
            switch (type) {
                case "KeyboardEvent":
                    return context.deserialize(json, KeyboardEvent.class);
                case "MouseButtonEvent":
                    return context.deserialize(json, MouseButtonEvent.class);
                case "MousePosEvent":
                    return context.deserialize(json, MousePosEvent.class);
                case "MouseScrollEvent":
                    return context.deserialize(json, MouseScrollEvent.class);
                default:
                    throw new JsonParseException("Unknown MyInputEvent type: " + type);
            }
        }
    }


}
