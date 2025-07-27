package Utils.DataStruct;

import java.util.Objects;

//  什么低版本评测机啊，连java.utils.pair都不支持
//  还得我自己手写(chao)一个是吧(大怒x
public class Pair<T1, T2>{
    private final T1 first;
    private final T2 second;

    public Pair(T1 first, T2 second) {
        this.first = first;
        this.second = second;
    }

    public T1 getFirst() {
        return first;
    }

    public T2 getSecond() {
        return second;
    }

    @Override
    public boolean equals(Object o) {
        if(o instanceof Pair<?, ?> pair){
            return first.equals(pair.first) &&
                    second.equals(pair.second);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(first, second);
    }


}

