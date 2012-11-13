package one.nio.serial;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class SerializerNotFoundException extends IOException implements Externalizable {
    private long uid;

    public SerializerNotFoundException(long uid) {
        this.uid = uid;
    }

    public long getUid() {
        return uid;
    }

    @Override
    public String toString() {
        return getClass().getName() + ": " + Long.toHexString(uid);
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeLong(uid);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException {
        this.uid = in.readLong();
    }
}
