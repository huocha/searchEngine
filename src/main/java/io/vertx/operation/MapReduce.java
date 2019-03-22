package io.vertx.operation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import io.vertx.structure.Index;

public class MapReduce {
        
        public static void main(String[] args) {
                
                // the problem:
                
                // from here (INPUT)
                
        		// <Row1>  <{ lastName: NGUYEN }><{ firstName: Jasmine, age: 21 }>
	        	// <Row1>  <{ lastName: NGUYEN }><{ firstName: Tra, age: 20 }>
	        	// <Row1>  <{ lastName: DAO }><{ firstName: Huong Tra, age: 22 }>
	        		
        		// we want to go to here (OUTPUT)
                
                // <{lastName: NGUYEN}><{firstName: Jasmine, age: 21} => 1, {firstName: Tra, age; 20} => 1>
        		
        		// <{ lastName: DAO }><{ firstName: Huong Tra, age: 22 } => 1>
                
                // in plain English we want to
                
                // Given a set of rows with key and value
                // we want to index them by word 
                // so I can return all rows that contain a given word
                // together with the number of occurrences of that word
                // without any sorting
                
                ////////////
                // INPUT:
                ///////////
                
        		/**
        		 * INPUT is [Row] Map => <keyIndex><value>
        		 */
        	
        	
                Map<String, String> input = new HashMap<String, String>();
                input.put("file1.txt", "foo foo bar cat dog dog");
                input.put("file2.txt", "foo house cat cat dog");
                input.put("file3.txt", "foo foo foo bird");
                
               
           
                
                // APPROACH #3: Distributed MapReduce
                {
                        final Map<String, Map<String, Integer>> output = new HashMap<String, Map<String, Integer>>();
                        
                        // MAP:
                        
                        final List<MappedItem> mappedItems = new LinkedList<MappedItem>();
                        
                        final MapCallback<String, MappedItem> mapCallback = new MapCallback<String, MappedItem>() {
                                @Override
                public synchronized void mapDone(String file, List<MappedItem> results) {
                    mappedItems.addAll(results);
                }
                        };
                        
                        List<Thread> mapCluster = new ArrayList<Thread>(input.size());
                        
                        Iterator<Map.Entry<String, String>> inputIter = input.entrySet().iterator();
                        while(inputIter.hasNext()) {
                                Map.Entry<String, String> entry = inputIter.next();
                                final String file = entry.getKey();
                                final String contents = entry.getValue();
                                
                                Thread t = new Thread(new Runnable() {
                                        @Override
                    public void run() {
                                                map(file, contents, mapCallback);
                    }
                                });
                                mapCluster.add(t);
                                t.start();
                        }
                        
                        // wait for mapping phase to be over:
                        for(Thread t : mapCluster) {
                                try {
                                        t.join();
                                } catch(InterruptedException e) {
                                        throw new RuntimeException(e);
                                }
                        }
                        
                        // GROUP:
                        
                        Map<String, List<String>> groupedItems = new HashMap<String, List<String>>();
                        
                        Iterator<MappedItem> mappedIter = mappedItems.iterator();
                        while(mappedIter.hasNext()) {
                                MappedItem item = mappedIter.next();
                                String word = item.getWord();
                                String file = item.getFile();
                                List<String> list = groupedItems.get(word);
                                if (list == null) {
                                        list = new LinkedList<String>();
                                        groupedItems.put(word, list);
                                }
                                list.add(file);
                        }
                        
                        // REDUCE:
                        
                        final ReduceCallback<String, String, Integer> reduceCallback = new ReduceCallback<String, String, Integer>() {
                                @Override
                public synchronized void reduceDone(String k, Map<String, Integer> v) {
                        output.put(k, v);
                }
                        };
                        
                        List<Thread> reduceCluster = new ArrayList<Thread>(groupedItems.size());
                        
                        Iterator<Map.Entry<String, List<String>>> groupedIter = groupedItems.entrySet().iterator();
                        while(groupedIter.hasNext()) {
                                Map.Entry<String, List<String>> entry = groupedIter.next();
                                final String word = entry.getKey();
                                final List<String> list = entry.getValue();
                                
                                Thread t = new Thread(new Runnable() {
                                        @Override
                    public void run() {
                                                reduce(word, list, reduceCallback);
                                        }
                                });
                                reduceCluster.add(t);
                                t.start();
                        }
                        
                        // wait for reducing phase to be over:
                        for(Thread t : reduceCluster) {
                                try {
                                        t.join();
                                } catch(InterruptedException e) {
                                        throw new RuntimeException(e);
                                }
                        }
                        
                        System.out.println(output);
                }
        }
        
        public static void map(String file, String contents, List<MappedItem> mappedItems) {
                String[] words = contents.trim().split("\\s+");
                for(String word: words) {
                        mappedItems.add(new MappedItem(word, file));
                }
        }
        
        public static void reduce(String word, List<String> list, Map<String, Map<String, Integer>> output) {
                Map<String, Integer> reducedList = new HashMap<String, Integer>();
                for(String file: list) {
                        Integer occurrences = reducedList.get(file);
                        if (occurrences == null) {
                                reducedList.put(file, 1);
                        } else {
                                reducedList.put(file, occurrences.intValue() + 1);
                        }
                }
                output.put(word, reducedList);
        }
        
        public static interface MapCallback<E, V> {
                
                public void mapDone(E key, List<V> values);
        }
        
        public static void map(String file, String contents, MapCallback<String, MappedItem> callback) {
                String[] words = contents.trim().split("\\s+");
                List<MappedItem> results = new ArrayList<MappedItem>(words.length);
                for(String word: words) {
                        results.add(new MappedItem(word, file));
                }
                callback.mapDone(file, results);
        }
        
        public static interface ReduceCallback<E, K, V> {
                
                public void reduceDone(E e, Map<K,V> results);
        }
        
        public static void reduce(String word, List<String> list, ReduceCallback<String, String, Integer> callback) {
                
                Map<String, Integer> reducedList = new HashMap<String, Integer>();
                for(String file: list) {
                        Integer occurrences = reducedList.get(file);
                        if (occurrences == null) {
                                reducedList.put(file, 1);
                        } else {
                                reducedList.put(file, occurrences.intValue() + 1);
                        }
                }
                callback.reduceDone(word, reducedList);
        }
        
        private static class MappedItem { 
                
                private final Index key;
                private static ArrayList<String> values = new ArrayList<>();
                
                public MappedItem(Index key, ArrayList<String> values) {
                        this.key = key;
                        this.values = values;
                }

                public Index getIndex() {
                        return key;
                }

                public ArrayList<String> getvalue() {
                        return values;
                }
                
                @Override
                public String toString() {
                        return "[\"" + key.toString() + "\",\"" + values.toString() + "\"]";
                }
        }
} 