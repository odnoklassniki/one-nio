package one.nio.rpc;

import java.io.Serializable;

public class Entity implements Serializable {
    private long id;
    private String data;

    public Entity(long id, String data) {
        this.id = id;
        this.data = data;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "Entity[id=" + id + ",data=" + data + "]";
    }
}
