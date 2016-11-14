package com.suntao;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collection;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class DomModifier {

    // DFS遍历所有节点
    private void dfs(Document document, Element root, Collection<Property> removedProps,
            Collection<Property> addedProps) {
        if (!root.hasChildNodes()) {
            return;
        }
        NodeList children = root.getChildNodes();
        int i = 0;
        int pos = -1;
        while (i < children.getLength()) {
            boolean flag = false;
            Node node = children.item(i);
            switch (node.getNodeType()) {
            case Node.TEXT_NODE:
                String text = node.getNodeValue();
                for (Property prop : removedProps) {
                    String regex = "[ \t]*(t1\\.)?" + Utils.camelCaseToUnderscore(prop.getName())
                            + ".*,?[ \t]*\n";
                    text = text.replaceAll(regex, "");
                }
                if (!removedProps.isEmpty()) {
                    // 若去掉的正好是最后一个属性, 那么from和where前面就会多出一个逗号
                    pos = text.indexOf("from");
                    if (pos != -1) {
                        String temp = Utils.rtrim(text.substring(0, pos));
                        if (temp.endsWith(",")) {
                            text = temp.substring(0, temp.length() - 1) + "\n\t"
                                    + text.substring(pos);
                        }
                    }
                    pos = text.indexOf("where");
                    if (pos != -1) {
                        String temp = Utils.rtrim(text.substring(0, pos));
                        if (temp.endsWith(",")) {
                            text = temp.substring(0, temp.length() - 1) + "\n\t"
                                    + text.substring(pos);
                        }
                    }
                }
                if (!addedProps.isEmpty()) {
                    Node parentNode = node.getParentNode();
                    if (parentNode.getNodeName().equals("select")) {
                        StringBuilder sb = new StringBuilder();
                        for (Property prop : addedProps) {
                            sb.append(",\n\t\tt1." + Utils.camelCaseToUnderscore(prop.getName()));
                        }
                        pos = text.indexOf("from");
                        if (pos != -1) {
                            text = Utils.rtrim(text.substring(0, pos)) + sb.append("\n\t")
                                    + text.substring(pos);
                        }
                    } else if (parentNode.getNodeName().equals("update")) {
                        Element element = (Element) parentNode;
                        // 只更新update*All方法，不更新update*Sensitive
                        if (element.getAttribute("id").contains("All")) {
                            StringBuilder sb = new StringBuilder();
                            for (Property prop : addedProps) {
                                sb.append(",\n\t\t\tt1."
                                        + Utils.camelCaseToUnderscore(prop.getName()) + " = #{"
                                        + prop.getName() + ",jdbcType=" + prop.getJdbcType() + "}");
                            }
                            pos = text.indexOf("where");
                            if (pos != -1) {
                                text = Utils.rtrim(text.substring(0, pos)) + sb.append("\n\t")
                                        + text.substring(pos);
                            }
                        }
                    }
                }
                node.setNodeValue(text);
                i++;
                break;
            case Node.ELEMENT_NODE:
                Element element = (Element) node;
                for (Property prop : removedProps) {
                    if (element.getAttribute("test").contains(prop.getName())
                            || element.getAttribute("property").equals(prop.getName())) {
                        // 删除该<result>之前的文本节点(即空白符)
                        node.getParentNode().removeChild(node.getPreviousSibling());
                        // 删除property属性值为该删属性之一的<result>元素
                        node.getParentNode().removeChild(node);
                        flag = true;
                    }
                }
                String nodeName = node.getNodeName();
                for (Property prop : addedProps) {
                    Element child;
                    Text textNode;
                    switch (nodeName) {
                    // 在<resultMap>元素中新建<result>子元素
                    case "resultMap":
                        textNode = document.createTextNode("\t\t");
                        node.appendChild(textNode);
                        child = document.createElement("result");
                        child.setAttribute("column", Utils.camelCaseToUnderscore(prop.getName())
                                .toUpperCase());
                        child.setAttribute("property", prop.getName());
                        child.setAttribute("jdbcType", prop.getJdbcType());
                        node.appendChild(child);
                        textNode = document.createTextNode("\n");
                        node.appendChild(textNode);
                        break;
                    // 在<select>元素中的<where>子元素中新建<if>子元素
                    case "where":
                        // 不是ByPage的select
                        Element parentElement = (Element) node.getParentNode();
                        if (!parentElement.getAttribute("id").contains("Page")) {
                            textNode = document.createTextNode("\t");
                            node.appendChild(textNode);
                            child = document.createElement("if");
                            child.setAttribute("test",
                                    prop.getName() + " != null and " + prop.getName() + " != ''");
                            textNode = document.createTextNode("and t1."
                                    + Utils.camelCaseToUnderscore(prop.getName()).toUpperCase()
                                    + " = #{" + prop.getName() + "}");
                            child.appendChild(textNode);
                            node.appendChild(child);
                            textNode = document.createTextNode("\n\t\t");
                            node.appendChild(textNode);
                        }
                        break;
                    case "if":
                        // <if test="bean != null">
                        if (element.getAttribute("test").equals("bean != null")) {
                            textNode = document.createTextNode("\t");
                            node.appendChild(textNode);
                            child = document.createElement("if");
                            child.setAttribute("test", "bean." + prop.getName()
                                    + " != null and bean." + prop.getName() + " != ''");
                            textNode = document.createTextNode("and t1."
                                    + Utils.camelCaseToUnderscore(prop.getName()).toUpperCase()
                                    + " = #{bean." + prop.getName() + "}");
                            child.appendChild(textNode);
                            node.appendChild(child);
                            textNode = document.createTextNode("\n\t\t\t");
                            node.appendChild(textNode);
                        }
                        break;
                    // 在<update>元素中的<set>子元素中新建<if>子元素
                    case "set":
                        textNode = document.createTextNode("\t\t");
                        node.appendChild(textNode);
                        child = document.createElement("if");
                        child.setAttribute("test", prop.getName() + " != null");
                        textNode = document.createTextNode("t1."
                                + Utils.camelCaseToUnderscore(prop.getName()) + " = #{"
                                + prop.getName() + ",jdbcType=" + prop.getJdbcType() + "},");
                        child.appendChild(textNode);
                        node.appendChild(child);
                        textNode = document.createTextNode("\n\t");
                        node.appendChild(textNode);
                        break;
                    // 在<insert>元素中的<trim>子元素中新建<if>子元素
                    case "trim":
                        if (!element.getAttribute("prefix").contains("values")) {
                            textNode = document.createTextNode("\t\t");
                            node.appendChild(textNode);
                            child = document.createElement("if");
                            child.setAttribute("test", prop.getName() + " != null");
                            textNode = document.createTextNode(Utils.camelCaseToUnderscore(prop
                                    .getName()) + ",");
                            child.appendChild(textNode);
                            node.appendChild(child);
                            textNode = document.createTextNode("\n\t");
                            node.appendChild(textNode);
                        } else {
                            textNode = document.createTextNode("\t\t");
                            node.appendChild(textNode);
                            child = document.createElement("if");
                            child.setAttribute("test", prop.getName() + " != null");
                            textNode = document.createTextNode("#{" + prop.getName() + ",jdbcType="
                                    + prop.getJdbcType() + "},");
                            child.appendChild(textNode);
                            node.appendChild(child);
                            textNode = document.createTextNode("\n\t");
                            node.appendChild(textNode);
                        }
                        break;
                    }
                }
                if (!flag) {
                    i++;
                    dfs(document, element, removedProps, addedProps);
                }
                break;
            default:
                i++;
            }
        }
    }

    private String prettyPrint(Document document) {
        DomWriter dw = new DomWriter();
        String s = dw.toString(document);
        return s;
    }

    public String modify(File xmlFile, Collection<Property> removedProps,
            Collection<Property> addedProps) throws ParserConfigurationException, SAXException,
            IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setExpandEntityReferences(false);
        factory.setIgnoringComments(false);
        factory.setValidating(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver(new EntityResolver() {
            @Override
            public InputSource resolveEntity(String publicId, String systemId) throws SAXException,
                    IOException {
                if (systemId.contains("mybatis-3-mapper.dtd")) {
                    // 不让联网下载dtd文件去验证xml, 否则离线的情况下不能使用
                    return new InputSource(new StringReader(""));
                }
                return null;
            }
        });
        Document document = builder.parse(xmlFile);
        Element rootElement = document.getDocumentElement();
        dfs(document, rootElement, removedProps, addedProps);
        return prettyPrint(document);
    }
}
