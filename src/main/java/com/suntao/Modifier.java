package com.suntao;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

public class Modifier {
    private static final String USAGE_STRING = "Usage: java -jar modifier.jar <源xml文件路径> <目的xml文件路径> "
            + "[-a <prop1[,jdbcType]> [<prop2[,jdbcType]> ...]] [-d <prop1> [<prop2> ...]]\n"
            + "其中两个文件路径是必须的选项; -a为可选选项, 指明要添加的属性, 对要添加的属性, jdbc类型可以不写, 默认为VARCHAR, 可选值有DATE, DECIMAL等;\n"
            + "-d为可选选项指明要删除的属性; 属性是DTO而不是数据库表的字段\n"
            + "例子：java -jar modifier.jar ExampleMapper.xml result.xml -a seqId,DATE seqNo -d faultType\n"
            + "Author: suntao, Bug Report: buzzerrookie@hotmail.com";

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println(USAGE_STRING);
            System.exit(-1);
        }
        File srcFile = new File(args[0]);
        List<Property> removedProps = new ArrayList<>();
        List<Property> addedProps = new ArrayList<>();
        for (int i = 2; i < args.length;) {
            if (args[i].equals("-a")) {
                for (int j = ++i; j < args.length; i++, j++) {
                    if (args[j].equals("-d")) {
                        break;
                    }
                    String[] array = args[j].split(",");
                    if (array.length == 1) {
                        addedProps.add(new Property(array[0], "VARCHAR")); // 默认是VARCHAR
                    } else {
                        addedProps.add(new Property(array[0], array[1]));
                    }
                }
            } else if (args[i].equals("-d")) {
                for (int j = ++i; j < args.length; i++, j++) {
                    if (args[j].equals("-a")) {
                        break;
                    }
                    String[] array = args[j].split(",");
                    removedProps.add(new Property(array[0], "")); // 删除时不用管jdbcType
                }
            }
        }
        String result = "";
        try {
            DomModifier merger = new DomModifier();
            result = merger.modify(srcFile, removedProps, addedProps);
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-2);
        }
        File dstFile = new File(args[1]);
        try {
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(
                    dstFile), "UTF-8"));
            bw.write(result);
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("写文件" + args[1] + "失败");
            System.exit(-3);
        }
    }
}
