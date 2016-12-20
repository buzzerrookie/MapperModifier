package com.suntao;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.IOUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.suntao.exceptions.ModifyException;

public class DomModifier {
    private List<Property> removedProps;
    private List<Property> addedProps;
    private DocumentBuilderFactory factory;
    private Document document;
    
    public DomModifier(List<Property> removedProps, List<Property> addedProps) {
        this.removedProps = new ArrayList<>(removedProps);
        this.addedProps = new ArrayList<>(addedProps);
        this.factory = DocumentBuilderFactory.newInstance();
        this.factory.setExpandEntityReferences(false);
        this.factory.setIgnoringComments(false);
        this.factory.setValidating(false);
    }

    /**
     * 修改mapper
     * 
     * @param srcFile 源文件
     * @param dstFile 目的文件
     * @author sunt
     * @since 2016年12月20日
     */
    public void modifyFile(File srcFile, File dstFile) {
        BufferedInputStream bis = null;
        BufferedWriter bw = null;
        try {
            bis = new BufferedInputStream(new FileInputStream(srcFile));
            String result = modify(bis);
            bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(dstFile), "UTF-8"));
            bw.write(result);
        } catch (FileNotFoundException e) {
            System.err.println("文件不存在" + e.getMessage());
            System.exit(-2);
        } catch (IOException e) {
            System.err.println("写文件" + dstFile.getName() + "失败");
            System.exit(-3);
        } catch (ModifyException e) {
            e.printStackTrace();
            System.exit(-4);
        } finally {
            IOUtils.closeQuietly(bis);
            IOUtils.closeQuietly(bw);
        }
    }
    
    /**
     * 修改xml输入流
     * 
     * @param is xml输入流
     * @return 修改后的xml
     * @throws ModifyException 修改失败时抛出
     * @author sunt
     * @since 2016年12月20日
     */
    private String modify(InputStream is) throws ModifyException {
        try {
            DocumentBuilder builder = this.factory.newDocumentBuilder();
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
            this.document = builder.parse(is);
            dfs(this.document.getDocumentElement());
            return prettyPrint(this.document);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new ModifyException("修改失败", e);
        }
    }
    
    /**
     * 深度优先遍历DOM文档
     * 
     * @param parent 遍历的起始元素
     * @author sunt
     * @since 2016年12月20日
     */
    private void dfs(Element parent) {
        if (!parent.hasChildNodes()) {
            return;
        }
        NodeList children = parent.getChildNodes();
        int i = 0;
        while (i < children.getLength()) {
            Node node = children.item(i);
            switch (node.getNodeType()) {
            case Node.TEXT_NODE:
                String text = addPropsInTextNode(node);
                text = removePropsInTextNode(text);
                node.setNodeValue(text);
                i++;
                break;
            case Node.ELEMENT_NODE:
                Element element = (Element) node;
                addPropsInElementNode(element);
                if (!removePropsInElementNode(element)) {
                    i++;
                    dfs(element);
                }
                break;
            default:
                i++;
            }
        }
    }

    /**
     * 为文本节点<code>node</code>添加属性
     * 
     * @param node 文本节点
     * @return 添加属性后的文本节点的文本
     * @author sunt
     * @since 2016年12月19日
     */
    private String addPropsInTextNode(Node node) {
        String text = node.getNodeValue();
        if (addedProps.isEmpty()) {
            return text;
        }
        Node parentNode = node.getParentNode();
        switch (parentNode.getNodeName()) {
        case "select":
            text = addPropsInSelectText(text);
            break;
        case "update":
            Element parentElement = (Element) parentNode;
            // 只更新update*All方法，不更新update*Sensitive
            if (parentElement.getAttribute("id").contains("All")) {
                text = addPropsInUpdateText(text);
            }
            break;
        }
        return text;
    }
    
    /**
     * 为select语句添加增加的属性
     * 
     * @param text 文本
     * @return 添加属性后的文本
     * @author sunt
     * @since 2016年12月19日
     */
    private String addPropsInSelectText(String text) {
        StringBuilder sb = new StringBuilder();
        for (Property prop : addedProps) {
            sb.append(",\n\t\tt1." + Utils.camelCaseToUnderscore(prop.getName()));
        }
        int pos = text.indexOf("from");
        if (pos != -1) {
            text = Utils.rtrim(text.substring(0, pos)) + sb.append("\n\t") + text.substring(pos);
        }
        return text;
    }
    
    /**
     * 为update语句添加属性
     * 
     * @param text 文本
     * @return 添加属性后的文本
     * @author sunt
     * @since 2016年12月19日
     */
    private String addPropsInUpdateText(String text) {
        StringBuilder sb = new StringBuilder();
        for (Property prop : addedProps) {
            sb.append(",\n\t\t\tt1." + Utils.camelCaseToUnderscore(prop.getName())
                    + " = #{" + prop.getName() + ",jdbcType=" + prop.getJdbcType() + "}");
        }
        int pos = text.indexOf("where");
        if (pos != -1) {
            text = Utils.rtrim(text.substring(0, pos)) + sb.append("\n\t") + text.substring(pos);
        }
        return text;
    }

    /**
     * 从文本节点的文本中删除属性
     * 
     * @param text 文本节点的文本
     * @return 删除属性后的文本
     * @author sunt
     * @since 2016年12月19日
     */
    private String removePropsInTextNode(String text) {
        for (Property prop : removedProps) {
            /**
             * 不能用String regex = "[ \t]*(t1\\.){1}" + Utils.camelCaseToUnderscore(prop.getName())
             *      + ".*,?[ \t]*\n";
             * 这样当xxx字段是另一字段子串时，点号匹配了任意字符会出问题
             */
            // 删除select语句里面的字段t1.xxx
            String regex = "[ \t]*(t1\\.){1}" + Utils.camelCaseToUnderscore(prop.getName())
                    + "[ \t]*,?[ \t]*\n";
            text = text.replaceAll(regex, "");
            // 删除update语句里面t1.xxx  =#{dto.xxx,jdbcType=DECIMAL},
            regex = "[ \t]*(t1\\.){1}" + Utils.camelCaseToUnderscore(prop.getName())
                    + "[ \t]*=.+,?[ \t]*\n";
            text = text.replaceAll(regex, "");
        }
        if (!removedProps.isEmpty()) {
            // 若去掉的正好是最后一个属性, 那么select语句的from和update语句的where前面就会多出一个逗号
            text = removeCommaBeforeKeyword(text, "from");
            text = removeCommaBeforeKeyword(text, "where");
        }
        return text;
    }

    /**
     * 从文本<code>text</code>中移除关键字<code>keyword</code>前面的逗号
     * 
     * @param text 文本
     * @param keyword 关键字
     * @return 移除逗号后的文本
     * @author sunt
     * @since 2016年12月19日
     */
    private String removeCommaBeforeKeyword(String text, String keyword) {
        int pos = text.indexOf(keyword);
        if (pos != -1) {
            String strBeforeKeyword = Utils.rtrim(text.substring(0, pos));
            if (strBeforeKeyword.endsWith(",")) {
                text = strBeforeKeyword.substring(0, strBeforeKeyword.length() - 1) + "\n\t"
                        + text.substring(pos);
            }
        }
        return text;
    }

    /**
     * 为元素节点添加属性
     * 
     * @param element 元素节点
     * @author sunt
     * @since 2016年12月19日
     */
    private void addPropsInElementNode(Element element) {
        String nodeName = element.getNodeName();
        for (Property prop : addedProps) {
            switch (nodeName) {
            case "resultMap":
                addPropInResultMap(element, prop);
                break;
            case "where":
                addPropInWhere(element, prop);
                break;
            case "if":
                addPropInIf(element, prop);
                break;
            case "set":
                addPropInSet(element, prop);
                break;
            case "trim":
                addPropInTrim(element, prop);
                break;
            }
        }
    }
    
    /**
     * 在&lt;resultMap&gt;元素中新建&lt;result&gt;子元素
     * 
     * @param element 元素节点
     * @param prop 要添加的属性
     * @author sunt
     * @since 2016年12月19日
     */
    private void addPropInResultMap(Element element, Property prop) {
        element.appendChild(document.createTextNode("\t"));
        Element child = document.createElement("result");
        child.setAttribute("column", Utils.camelCaseToUnderscore(prop.getName()).toUpperCase());
        child.setAttribute("property", prop.getName());
        child.setAttribute("jdbcType", prop.getJdbcType());
        element.appendChild(child);
        element.appendChild(document.createTextNode("\n"));
    }
    
    /**
     * 在方法名不以ByPage结尾的&lt;select&gt;元素中的&lt;where&gt;子元素中新建if子元素
     * 
     * @param element 元素节点
     * @param prop 要添加的属性
     * @author sunt
     * @since 2016年12月19日
     */
    private void addPropInWhere(Element element, Property prop) {
        Element parentElement = (Element) element.getParentNode();
        if (!parentElement.getAttribute("id").contains("Page")) {
            element.appendChild(document.createTextNode("\t"));
            Element child = document.createElement("if");
            child.setAttribute("test", prop.getName() + " != null and " + prop.getName() + " != ''");
            Text textNode = document.createTextNode("and t1."
                    + Utils.camelCaseToUnderscore(prop.getName()).toUpperCase() + " = #{"
                    + prop.getName() + "}");
            child.appendChild(textNode);
            element.appendChild(child);
            element.appendChild(document.createTextNode("\n\t\t"));
        }
    }
    
    /**
     * 在方法名以ByPage结尾的select元素的&lt;if test="bean != null"&gt;元素中增加if子元素
     * 
     * @param element 元素节点
     * @param prop 要添加的属性
     * @author sunt
     * @since 2016年12月19日
     */
    private void addPropInIf(Element element, Property prop) {
        if (element.getAttribute("test").equals("bean != null")) {
            element.appendChild(document.createTextNode("\t"));
            Element child = document.createElement("if");
            child.setAttribute("test", "bean." + prop.getName() + " != null and bean."
                    + prop.getName() + " != ''");
            Text textNode = document.createTextNode("and t1."
                    + Utils.camelCaseToUnderscore(prop.getName()).toUpperCase() + " = #{bean."
                    + prop.getName() + "}");
            child.appendChild(textNode);
            element.appendChild(child);
            element.appendChild(document.createTextNode("\n\t\t\t"));
        }
    }
    
    /**
     * 在&lt;update&gt;元素中的&lt;set&gt;子元素中新建&lt;if&gt;子元素
     * 
     * @param element 元素节点
     * @param prop 要添加的属性
     * @author sunt
     * @since 2016年12月19日
     */
    private void addPropInSet(Element element, Property prop) {
        element.appendChild(document.createTextNode("\t\t"));
        Element child = document.createElement("if");
        child.setAttribute("test", prop.getName() + " != null");
        Text textNode = document.createTextNode("t1."
                + Utils.camelCaseToUnderscore(prop.getName()) + " = #{"
                + prop.getName() + ",jdbcType=" + prop.getJdbcType() + "},");
        child.appendChild(textNode);
        element.appendChild(child);
        element.appendChild(document.createTextNode("\n\t"));
    }
    
    /**
     * 在&lt;insert&gt;元素中的&lt;trim&gt;子元素中新建&lt;if&gt;子元素
     * 
     * @param element 元素节点
     * @param prop 要添加的属性
     * @author sunt
     * @since 2016年12月19日
     */
    private void addPropInTrim(Element element, Property prop) {
        Text textNode;
        element.appendChild(document.createTextNode("\t\t"));
        Element child = document.createElement("if");
        child.setAttribute("test", prop.getName() + " != null");
        if (!element.getAttribute("prefix").contains("values")) {
            // insert into table_name(here) values(...) 
            textNode = document.createTextNode(Utils.camelCaseToUnderscore(prop.getName()) + ",");
        } else {
            // insert into table_name(...) values(here) 
            textNode = document.createTextNode("#{" + prop.getName() + ",jdbcType="
                    + prop.getJdbcType() + "},");
        }
        child.appendChild(textNode);
        element.appendChild(child);
        element.appendChild(document.createTextNode("\n\t"));
    }

    /**
     * 尝试从DOM中移除含有需要删除属性的&lt;result&gt;元素和&lt;if&gt;元素
     * 
     * @param element 元素节点
     * @return 是否删除了该元素，删除返回true，否则返回false
     * @author sunt
     * @since 2016年12月19日
     */
    private boolean removePropsInElementNode(Element element) {
        for (Property prop : removedProps) {
            if (element.getAttribute("test").contains(prop.getName())
                    || element.getAttribute("property").equals(prop.getName())) {
                // 删除之前的文本节点(即空白符)
                element.getParentNode().removeChild(element.getPreviousSibling());
                // 删除元素
                element.getParentNode().removeChild(element);
                return true;
            }
        }
        return false;
    }
    
    private String prettyPrint(Document document) {
        DomWriter dw = new DomWriter();
        return dw.toString(document);
    }
}
