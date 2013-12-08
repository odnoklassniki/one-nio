package one.nio.serial.gen;

import one.nio.serial.CalcSizeStream;
import one.nio.serial.DataStream;

import java.io.IOException;

public interface Delegate {
    void calcSize(Object obj, CalcSizeStream css) throws IOException;
    void write(Object obj, DataStream out) throws IOException;
    Object read(DataStream in) throws IOException, ClassNotFoundException;
    void toJson(Object obj, StringBuilder builder) throws IOException;
}
