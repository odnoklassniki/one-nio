package one.nio.serial.evolution.sample;

import java.io.Serializable;
import java.util.Objects;

public class Base implements Serializable {
    String name;

    public Base(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Base base = (Base) o;
        return Objects.equals(name, base.name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }
}
