package one.nio.serial.gen;

import one.nio.serial.CalcSizeStream;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public interface Delegate {
    void calcSize(Object obj, CalcSizeStream css) throws IOException;
    void write(Object obj, ObjectOutput out) throws IOException;
    Object read(ObjectInput in) throws IOException, ClassNotFoundException;
    void fill(Object obj, ObjectInput in) throws IOException;
    void skip(ObjectInput in) throws IOException;
}
