//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2015.08.12 at 04:40:22 PM CEST 
//


package com.bc.calvalus.wps2.jaxb;

import com.bc.calvalus.wps2.responses.CapabilitiesBuilder;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for anonymous complex type.
 * <p/>
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p/>
 * <pre>
 * &lt;complexType>
 *   &lt;complexContent>
 *     &lt;extension base="{http://www.opengis.net/ows/1.1}CapabilitiesBaseType">
 *       &lt;sequence>
 *         &lt;element ref="{http://www.opengis.net/wps/1.0.0}ProcessOfferings"/>
 *         &lt;element ref="{http://www.opengis.net/wps/1.0.0}Languages"/>
 *         &lt;element ref="{http://www.opengis.net/wps/1.0.0}WSDL" minOccurs="0"/>
 *       &lt;/sequence>
 *       &lt;attribute name="service" use="required" type="{http://www.w3.org/2001/XMLSchema}anySimpleType" fixed="WPS" />
 *       &lt;attribute ref="{http://www.w3.org/XML/1998/namespace}lang use="required""/>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
            "processOfferings",
            "languages",
            "wsdl"
})
@XmlRootElement(name = "parameters")
public class L3Parameters {

    @XmlElement(name = "compositingType")
    protected String compositingType;
    @XmlElement(name = "Languages", required = true)
    protected Languages languages;
    @XmlElement(name = "WSDL")
    protected WSDL wsdl;
    @XmlAttribute(name = "service", required = true)
    @XmlSchemaType(name = "anySimpleType")
    protected String service;
    @XmlAttribute(name = "lang", namespace = "http://www.w3.org/XML/1998/namespace", required = true)
    protected String lang;

    /**
     * Gets the value of the processOfferings property.
     *
     * @return possible object is
     * {@link ProcessOfferings }
     */
    public ProcessOfferings getProcessOfferings() {
        return processOfferings;
    }

    /**
     * Sets the value of the processOfferings property.
     *
     * @param value allowed object is
     *              {@link ProcessOfferings }
     */
    public void setProcessOfferings(ProcessOfferings value) {
        this.processOfferings = value;
    }

    /**
     * List of the default and other languages supported by this service.
     *
     * @return possible object is
     * {@link Languages }
     */
    public Languages getLanguages() {
        return languages;
    }

    /**
     * Sets the value of the languages property.
     *
     * @param value allowed object is
     *              {@link Languages }
     */
    public void setLanguages(Languages value) {
        this.languages = value;
    }

    /**
     * Location of a WSDL document which describes the entire service.
     *
     * @return possible object is
     * {@link WSDL }
     */
    public WSDL getWSDL() {
        return wsdl;
    }

    /**
     * Sets the value of the wsdl property.
     *
     * @param value allowed object is
     *              {@link WSDL }
     */
    public void setWSDL(WSDL value) {
        this.wsdl = value;
    }

    /**
     * Gets the value of the service property.
     *
     * @return possible object is
     * {@link String }
     */
    public String getService() {
        if (service == null) {
            return "WPS";
        } else {
            return service;
        }
    }

    /**
     * Sets the value of the service property.
     *
     * @param value allowed object is
     *              {@link String }
     */
    public void setService(String value) {
        this.service = value;
    }

    /**
     * Gets the value of the lang property.
     *
     * @return possible object is
     * {@link String }
     */
    public String getLang() {
        return lang;
    }

    /**
     * Sets the value of the lang property.
     *
     * @param value allowed object is
     *              {@link String }
     */
    public void setLang(String value) {
        this.lang = value;
    }

}
