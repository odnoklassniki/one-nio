package one.nio.serial;

import org.junit.Assert;

import java.io.IOException;

abstract public class AbstractEvolutionTest {

    protected void doTest(Object oldObject, Object newObject) throws IOException, ClassNotFoundException {
        Serializer<?> oldSerializer = Repository.get(oldObject.getClass());
        try {
            Repository.removeSerializer(oldSerializer.uid);
            oldSerializer = Repository.get(oldObject.getClass());
            Assert.assertEquals(oldSerializer.cls(), oldObject.getClass());

            byte[] bytes = Utils.serializeObject(oldObject);
            Repository.removeSerializer(oldSerializer.uid);

            Serializer<?> newSerializer = Repository.get(newObject.getClass());
            Assert.assertEquals(newSerializer.cls(), newObject.getClass());

            Repository.provideSerializer(new DelegatingSerializer(newSerializer, oldSerializer.uid));

            Object deserialized = new DeserializeStream(bytes).readObject();
            Assert.assertEquals(newObject, deserialized);
        } finally {
            Repository.removeSerializer(oldSerializer.uid);
        }
    }

    class DelegatingSerializer<T> extends Serializer<T> {

        private Serializer<T> delegate;

        protected DelegatingSerializer(Serializer<T> delegate, long uid) {
            super(delegate.cls());
            this.uid = uid;
            this.delegate = delegate;
        }

        @Override
        public void calcSize(T obj, CalcSizeStream css) throws IOException {
            delegate.calcSize(obj, css);
        }

        @Override
        public void write(T obj, DataStream out) throws IOException {
            delegate.write(obj, out);
        }

        @Override
        public T read(DataStream in) throws IOException, ClassNotFoundException {
            return delegate.read(in);
        }

        @Override
        public void skip(DataStream in) throws IOException, ClassNotFoundException {
            delegate.skip(in);
        }

        @Override
        public void toJson(T obj, StringBuilder builder) throws IOException {
            delegate.toJson(obj, builder);
        }

        @Override
        public T fromJson(JsonReader in) throws IOException, ClassNotFoundException {
            return delegate.fromJson(in);
        }
    }
}
