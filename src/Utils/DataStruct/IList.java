package Utils.DataStruct;


import java.util.Iterator;

/** 该类实现了一个自定义的双向链表结构，目的是规避 Java 标准库中 LinkedList 为保证安全性而引入的多种限制（如 fail-fast 机制、结构修改检查等）。
 IList 表示链表本身，INode 表示链表中的节点。泛型参数 N 代表节点存储的数据类型，L 代表链表的附加属性类型。
 该实现支持链表头尾插入、节点插入与删除、索引更新、链表合并等操作。 */
public class IList<N, L> implements Iterable<IList.INode<N, L>> {
    private INode<N, L> head;
    private INode<N, L> tail;
    private L value;
    private int size;

    public IList(L value) {
        this.value = value;
        this.head = null;
        this.tail = null;
        this.size = 0;
    }

    public INode<N, L> getHead() {
        return head;
    }

    private void setHead(INode<N, L> head) {
        this.head = head;
    }

    public INode<N, L> getTail() {
        return tail;
    }

    private void setTail(INode<N, L> tail) {
        this.tail = tail;
    }

    public L getValue() {
        return value;
    }

    public void setValue(L value) {
        this.value = value;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    private void addNode() {
        this.size++;
    }

    private void removeNode() {
        this.size--;
    }

    public void add(INode<N, L> node){
        node.insertListEnd(this);
    }

    public void addToHead(INode<N, L> node){
        node.insertListHead(this);
    }

    public N getHeadValue(){
        return getHead().getValue();
    }

    public N getTailValue(){
        return getTail().getValue();
    }

    public boolean isEmpty() {
        return (this.head == null) && (this.tail == null) && (getSize() == 0);
    }

    @Override
    public Iterator<INode<N, L>> iterator() {
        return new ListIterator(this.getHead());
    }

    public void updateIndex(){
        int i= 0;
        for(var node : this){
            node.setIndexInList(i);
            i+=1;
        }
    }

    class ListIterator implements Iterator<INode<N, L>> {
        INode<N, L> tmpNode = new INode<>(null);
        INode<N, L> next = null;

        ListIterator(INode<N, L> head) {
            tmpNode.next = head;
        }

        @Override
        public boolean hasNext() {
            return next != null || tmpNode.next != null;
        }

        @Override
        public INode<N, L> next() {
            if (next == null) {
                tmpNode = tmpNode.next;
            } else {
                tmpNode = next;
            }
            next = null;
            return tmpNode;
        }

        @Override
        public void remove() {
            INode<N, L> prev = tmpNode.prev;
            INode<N, L> next = tmpNode.next;
            IList<N, L> parent = tmpNode.parent;

            if (prev != null) {
                prev.setNext(next);
            }
            else {
                parent.setHead(next);
            }

            if (next != null) {
                next.setPrev(prev);
            }
            else {
                parent.setTail(prev);
            }

            parent.removeNode();

            this.next = next;
            tmpNode.next = tmpNode.prev = null;
            tmpNode.value = null;
        }
    }

    public static class INode<N, L> {
        // N for list's node value type
        private N value;
        private INode<N, L> prev = null;
        private INode<N, L> next = null;

        private IList<N, L> parent;

        private int indexOfList;

        public INode(N val) {
            this.value = val;
            this.parent = null;
        }

        //  1. 将节点插入到某链表的头部
        public void insertListHead(IList<N, L> parent) {
            this.parent = parent;
            if (parent.isEmpty()) {
                parent.setHead(this);
                parent.setTail(this);
                parent.addNode();
            }
            else {
                insertBefore(parent.getHead());
            }
        }

        //  2. 将节点插入在某链表的尾部
        public void insertListEnd(IList<N, L> parent) {
            this.parent = parent;
            if (parent.isEmpty()) {
                parent.setHead(this);
                parent.setTail(this);
                parent.addNode();
            }
            else {
                insertAfter(parent.getTail());
            }
        }

        //  3. 将节点插入到某节点的后面
        public void insertAfter(INode<N, L> preNode) {

            this.next = preNode.next;
            preNode.next = this;
            this.prev = preNode;

            if (this.next != null) {
                this.next.setPrev(this);
            }

            this.parent = preNode.getParent();
            this.parent.addNode();

            if (this.parent.getTail() == preNode) {
                this.getParent().setTail(this);
            }
        }

        //  4. 将节点插入到某节点的前面
        public void insertBefore(INode<N, L> nxtNode) {
            this.next = nxtNode;
            this.prev = nxtNode.prev;
            nxtNode.prev = this;

            if (this.prev != null) {
                this.prev.setNext(this);
            }

            this.parent = nxtNode.getParent();
            this.parent.addNode();

            if (this.parent.getHead() == nxtNode) {
                this.parent.setHead(this);
            }
        }

        //  5. 将某节点移出list
        public void removeFromList() {
            parent.removeNode();
            if (parent.getHead() == this) {
                parent.setHead(this.next);
            }
            if (parent.getTail() == this) {
                parent.setTail(this.prev);
            }

            if (this.prev != null && this.next != null) {
                this.prev.setNext(this.next);
                this.next.setPrev(this.prev);
            }
            else if (this.prev != null) {
                this.prev.setNext(null);
            }
            else if (this.next != null){
                this.next.setPrev(null);
            }

            this.prev = this.next = null;
            this.parent = null;
        }

        public int getIndexInList(){
            return indexOfList;
        }

        public void setIndexInList(int idx){
            indexOfList = idx;
        }
        public N getValue() {
            return value;
        }

        public void setValue(N value) {
            this.value = value;
        }

        public INode<N, L> getPrev() {
            return prev;
        }

        public void setPrev(INode<N, L> prev) {
            this.prev = prev;
        }

        public INode<N, L> getNext() {
            return next;
        }

        public void setNext(INode<N, L> next) {
            this.next = next;
        }

        public IList<N, L> getParent() {
            return parent;
        }
    }

    public void addListBefore(IList<N, L> suclist) {
        if (this.tail == null) {
            assert isEmpty();
            this.setHead(suclist.getHead());
        } else {
            this.tail.setNext(suclist.getHead());
        }
        this.size += suclist.getSize();
        suclist.getHead().setPrev(this.tail);
        for (INode<N, L> sucnode = suclist.getHead(); sucnode != null; sucnode = sucnode.getNext()) {
            sucnode.parent = this;
        }
        this.tail = suclist.getTail();
    }
}