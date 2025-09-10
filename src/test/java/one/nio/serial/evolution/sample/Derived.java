package one.nio.serial.evolution.sample;

import java.util.Objects;

public class Derived extends Base {
    private final int id;

    public Derived(String name, int id) {
        super(name);
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Derived derived = (Derived) o;
        return Objects.equals(id, derived.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
