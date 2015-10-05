package com.company;

import java.io.Console;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {

        String preOrder = new String("1,2,4,#,#,5,#,#,3,#,#");
        BTree tree = new BTree(preOrder);
        System.out.println(tree.preOrder());

        List<Integer> foo = new ArrayList<Integer>();

    }
}

import java.io.*;
        import java.util.*;

/*
 * To execute Java, please define "static void main" on a class
 * named Solution.
 *
 * If you need more classes, simply define them inline.
 */

class Solution {
    public static void main(String[] args) {

        Graph g = new Graph();
        for(int i=0;i<10;i++){
            Node n = new Node(i);
            g.addNode(n);

        }

        for (int i=0;i<20;i++) {
            Node temp = g.searchNodeWithData(i);

            if(temp == null){
                System.out.println("Node with value " + i + " not found");
            }else{
                System.out.println("Found Node with Value " +temp.data);
            }
        }
    }
}
//
//class Node {
//    int data ;
//    List<Node> neighbors;
//
//    public Node(int data){
//        this.data = data;
//        neighbors = new ArrayList<>();
//    }
//
//    public void addNeighbor(Node n){
//        neighbors.add(n);
//    }
//}
//
//class Graph{
//    List<Node> graphNodes;
//
//    public Graph(){
//        graphNodes = new ArrayList<>();
//    }
//    public void addNode(Node n){
//        graphNodes.add(n);
//    }
//
//    public Node searchNodeWithData(int data){
//        for(Node n:graphNodes ){
//            if(n.data == data)return n;
//
//        }
//        return null;
//    }
}

