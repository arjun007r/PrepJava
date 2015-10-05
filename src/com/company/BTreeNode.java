package com.company;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by araja on 9/8/2015.
 */
public class BTreeNode {

    public int data;
    public BTreeNode left;
    public BTreeNode right;
    List<BTreeNode> neighbors;
    public BTreeNode(int data){
        this.data = data;
        neighbors = new ArrayList<BTreeNode>();
    }
    public void addNode(BTreeNode){
//        neighbors.add()
    }

}
