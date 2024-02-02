package xyz.srnyx.howdyholidays.mongo;

import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.types.ObjectId;

import org.jetbrains.annotations.Nullable;


public class Profile {
    @BsonProperty(value = "_id") public ObjectId id;
    public Long user;
    @Nullable public Integer presents;
    @Nullable public Long lastDaily;

    public Profile() {}

    public Profile(long user) {
        this.user = user;
    }

    public int getPresents() {
        return presents == null ? 0 : presents;
    }
}
