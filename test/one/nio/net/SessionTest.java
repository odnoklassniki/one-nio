package one.nio.net;

import one.nio.http.ResponseListener;
import one.nio.mem.DirectMemory;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

public class SessionTest {

    Socket mockedSocket = Mockito.mock(Socket.class);
    ResponseListener mockedListener = Mockito.mock(ResponseListener.class);
    long nativeAddress = 123L;
    byte[] array = new byte[0];

    @Test
    public void testArrayAndNativeItemWriteMultipleSocketInteractions() throws IOException {
        Session.ArrayAndNativeQueueItem item = new Session.ArrayAndNativeQueueItem(
                array, 5, nativeAddress, 9, mockedListener);

        ArgumentCaptor<Integer> offsetArray = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> lengthArray = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> offsetNative = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> lengthNative = ArgumentCaptor.forClass(Integer.class);

        Mockito.when(mockedSocket.write(eq(array), offsetArray.capture(), lengthArray.capture(), eq(0))).
                thenReturn(2).
                thenReturn(0).
                thenReturn(3).thenThrow(IllegalStateException.class);
        Mockito.when(mockedSocket.writeRaw(offsetNative.capture(), lengthNative.capture(), eq(0))).
                thenReturn(4).
                thenReturn(0).
                thenReturn(5).thenThrow(IllegalStateException.class);

        // nothing written initially
        verifyState(item, 0, 14);
        // write array 2 bytes
        assertEquals(2, item.write(mockedSocket));
        verifyState(item, 2, 14);
        // write array 0 bytes
        assertEquals(0, item.write(mockedSocket));
        verifyState(item, 2, 14);
        // write array 3 bytes and continue on native 4 bytes
        assertEquals(3 + 4, item.write(mockedSocket));
        verifyState(item, 9, 14);
        // write 0 bytes
        assertEquals(0, item.write(mockedSocket));
        verifyState(item, 9, 14);
        // finish write native, 5 bytes
        assertEquals(5, item.write(mockedSocket));
        verifyState(item, 14, 14);
        // nothing to write
        assertEquals(0, item.write(mockedSocket));
        verifyState(item, 14, 14);

        assertEquals(Arrays.asList(0, 2, 2), offsetArray.getAllValues());
        assertEquals(Arrays.asList(5, 3 ,3), lengthArray.getAllValues());
        assertEquals(Arrays.asList(nativeAddress, nativeAddress + 4, nativeAddress + 4), offsetNative.getAllValues());
        assertEquals(Arrays.asList(9, 5, 5), lengthNative.getAllValues());
    }

    public void verifyState(Session.ArrayAndNativeQueueItem item, int writtenTotal, int total) {
        assertEquals(total - writtenTotal, item.remaining());
        item.release(null);
        verify(mockedListener).onDone(writtenTotal, total, null);
        reset(mockedListener);
    }

    @Test
    public void testArrayAndNativeItemWriteSingleSocketInteraction() throws IOException {
        Session.ArrayAndNativeQueueItem item = new Session.ArrayAndNativeQueueItem(array, 5, nativeAddress, 9, null);

        ArgumentCaptor<Integer> offsetArray = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> lengthArray = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> offsetNative = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> lengthNative = ArgumentCaptor.forClass(Integer.class);

        Mockito.when(mockedSocket.write(eq(array), offsetArray.capture(), lengthArray.capture(), eq(0))).thenReturn(5).
                thenThrow(IllegalStateException.class);
        Mockito.when(mockedSocket.writeRaw(offsetNative.capture(), lengthNative.capture(), eq(0))).thenReturn(9).
                thenThrow(IllegalStateException.class);

        assertEquals(14, item.remaining());
        assertEquals(14, item.write(mockedSocket)); // everything written
        assertEquals(0, item.remaining());
        assertEquals(0, item.write(mockedSocket)); // nothing more should be written
        assertEquals(0, item.remaining());

        assertEquals(Collections.singletonList(0), offsetArray.getAllValues());
        assertEquals(Collections.singletonList(5), lengthArray.getAllValues());
        assertEquals(Collections.singletonList(nativeAddress), offsetNative.getAllValues());
        assertEquals(Collections.singletonList(9), lengthNative.getAllValues());
    }

    @Test
    public void testArrayAndNativeItemWriteJustArray() throws IOException {
        Session.ArrayAndNativeQueueItem item = new Session.ArrayAndNativeQueueItem(array, 5, 0, 0, null);

        ArgumentCaptor<Integer> offsetArray = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> lengthArray = ArgumentCaptor.forClass(Integer.class);

        Mockito.when(mockedSocket.write(eq(array), offsetArray.capture(), lengthArray.capture(), eq(0))).thenReturn(5).
                thenThrow(IllegalStateException.class);
        Mockito.when(mockedSocket.writeRaw(ArgumentMatchers.anyLong(), ArgumentMatchers.anyInt(), eq(0))).
                thenThrow(IllegalStateException.class);

        assertEquals(5, item.remaining());
        assertEquals(5, item.write(mockedSocket)); // everything written
        assertEquals(0, item.remaining());

        assertEquals(Collections.singletonList(0), offsetArray.getAllValues());
        assertEquals(Collections.singletonList(5), lengthArray.getAllValues());
    }

    @Test
    public void testArrayAndNativeItemJustNative() throws IOException {
        Session.ArrayAndNativeQueueItem item = new Session.ArrayAndNativeQueueItem(null, 0, nativeAddress, 9, null);

        ArgumentCaptor<Integer> offsetNative = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> lengthNative = ArgumentCaptor.forClass(Integer.class);

        Mockito.when(mockedSocket.write(ArgumentMatchers.any(byte[].class), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt())).
                thenThrow(IllegalStateException.class);
        Mockito.when(mockedSocket.writeRaw(offsetNative.capture(), lengthNative.capture(), eq(0))).thenReturn(9).
                thenThrow(IllegalStateException.class);

        assertEquals(9, item.remaining());
        assertEquals(9, item.write(mockedSocket)); // everything written
        assertEquals(0, item.remaining());
        assertEquals(0, item.write(mockedSocket)); // nothing more should be written
        assertEquals(0, item.remaining());

        assertEquals(Collections.singletonList(nativeAddress), offsetNative.getAllValues());
        assertEquals(Collections.singletonList(9), lengthNative.getAllValues());
    }
}
