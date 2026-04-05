package com.company;

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
