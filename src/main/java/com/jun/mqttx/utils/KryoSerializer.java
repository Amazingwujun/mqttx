package com.jun.mqttx.utils;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import io.netty.util.concurrent.FastThreadLocal;

import java.io.ByteArrayOutputStream;

/**
 * 基于 <strong>kryo</strong> 序列化框架实现
 */
public class KryoSerializer implements Serializer {

    private final FastThreadLocal<Kryo> holder = new FastThreadLocal<Kryo>(){
        @Override
        protected Kryo initialValue() {
            Kryo kryo = new Kryo();
            kryo.setRegistrationRequired(false);
            return kryo;
        }
    };

    @Override
    public byte[] serialize(Object target) {
        Kryo kryo = holder.get();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Output output = new Output(bos);
        kryo.writeClassAndObject(output, target);
        output.close();
        return bos.toByteArray();
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        Kryo kryo = holder.get();

        //noinspection unchecked
        return (T) kryo.readClassAndObject(new Input(bytes));
    }
}
