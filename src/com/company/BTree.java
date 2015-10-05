package com.company;

import com.sun.xml.internal.ws.util.StringUtils;

/**
 * Created by araja on 9/8/2015.
 */
public class BTree {

    BTreeNode root;
    private  static Integer currentIndex;
    // Given PreOrder Traversal separated by ',' and '#' for null demarking, it builds the tree. Only numbers is supported
    public  BTree(String preOrder){

        if(preOrder == null || preOrder.isEmpty())return;

        String[] sArray = preOrder.split(",");

        currentIndex =0;
        root =  buildBtree(sArray);

    }

    private BTreeNode buildBtree(String[] dataArray){

        if(currentIndex > dataArray.length -1 )return null;

        if(dataArray[currentIndex].equals("#")){
            currentIndex++;
            return null;
        }
        int number = Integer.parseInt(dataArray[currentIndex]);
        BTreeNode node = new BTreeNode(number);
        currentIndex ++;
        node.left = buildBtree(dataArray);
        node.right = buildBtree(dataArray);

        return node;


    }


    public String preOrder(){
       StringBuilder pre = new StringBuilder();
       preOrderR(root,pre);
        return pre.toString();

    }


    public void preOrderR(BTreeNode node , StringBuilder pre){

        if(node == null){
            if(!pre.toString().isEmpty()){
                pre.append("#,");
            }
            return;
        }
        pre.append(node.data + ",");
        preOrderR(node.left, pre);
        preOrderR(node.right,pre);
    }

    public BTreeNode getRoot() {
        return root;
    }
}
