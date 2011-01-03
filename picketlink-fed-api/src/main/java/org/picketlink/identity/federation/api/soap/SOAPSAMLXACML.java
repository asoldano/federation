/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors. 
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.picketlink.identity.federation.api.soap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;

import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPConnection;
import javax.xml.soap.SOAPConnectionFactory;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.security.xacml.core.model.context.DecisionType;
import org.jboss.security.xacml.core.model.context.RequestType;
import org.jboss.security.xacml.core.model.context.ResultType;
import org.picketlink.identity.federation.core.exceptions.ConfigurationException;
import org.picketlink.identity.federation.core.exceptions.ParsingException;
import org.picketlink.identity.federation.core.exceptions.ProcessingException;
import org.picketlink.identity.federation.core.parsers.saml.SAMLResponseParser;
import org.picketlink.identity.federation.core.parsers.util.StaxParserUtil;
import org.picketlink.identity.federation.core.saml.v2.common.IDGenerator;
import org.picketlink.identity.federation.core.saml.v2.constants.JBossSAMLConstants;
import org.picketlink.identity.federation.core.saml.v2.util.DocumentUtil;
import org.picketlink.identity.federation.core.saml.v2.util.XMLTimeUtil;
import org.picketlink.identity.federation.core.saml.v2.writers.SAMLRequestWriter;
import org.picketlink.identity.federation.core.util.StaxUtil;
import org.picketlink.identity.federation.newmodel.saml.v2.assertion.AssertionType;
import org.picketlink.identity.federation.newmodel.saml.v2.assertion.NameIDType;
import org.picketlink.identity.federation.newmodel.saml.v2.profiles.xacml.assertion.XACMLAuthzDecisionStatementType;
import org.picketlink.identity.federation.newmodel.saml.v2.profiles.xacml.protocol.XACMLAuthzDecisionQueryType;
import org.picketlink.identity.federation.newmodel.saml.v2.protocol.ResponseType;
import org.picketlink.identity.federation.org.xmlsoap.schemas.soap.envelope.Fault;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Class that deals with sending XACML
 * Request Response bundled in SAML pay load
 * as SOAP Requests
 * @author Anil.Saldhana@redhat.com
 * @since Jul 30, 2009
 */
public class SOAPSAMLXACML
{ 
   /**
    * Given an xacml request
    * @param endpoint
    * @param issuer
    * @param xacmlRequest
    * @return
    * @throws ProcessingException
    * @throws SOAPException 
    * @throws ParsingException 
    */
   public Result send(String endpoint, String issuer, RequestType xacmlRequest) throws ProcessingException, SOAPException, ParsingException
   { 
      try
      {
         String id = IDGenerator.create( "ID_" );
         
         XACMLAuthzDecisionQueryType queryType = new XACMLAuthzDecisionQueryType( id, JBossSAMLConstants.VERSION_2_0.get(),
               XMLTimeUtil.getIssueInstant() );
         
         queryType.setRequest(xacmlRequest);
         
         //Create Issuer
         NameIDType nameIDType = new NameIDType();
         nameIDType.setValue(issuer);
         queryType.setIssuer(nameIDType);
          
         
         
         
         MessageFactory messageFactory = MessageFactory.newInstance();
         
         SOAPMessage soapMessage = messageFactory.createMessage();
         
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         XMLStreamWriter xmlStreamWriter = StaxUtil.getXMLStreamWriter(baos);

         SAMLRequestWriter samlRequestWriter = new SAMLRequestWriter( xmlStreamWriter );
         samlRequestWriter.write( queryType );
         
         Document reqDocument = DocumentUtil.getDocument( new ByteArrayInputStream( baos.toByteArray() ));
         soapMessage.getSOAPBody().addDocument(reqDocument);
         
         
         /*Envelope envelope = createEnvelope(jaxbQueryType);
         
         JAXBElement<?> soapRequest = SOAPFactory.getObjectFactory().createEnvelope(envelope);
         
         Marshaller marshaller = SOAPSAMLXACMLUtil.getMarshaller();
         Unmarshaller unmarshaller = SOAPSAMLXACMLUtil.getUnmarshaller();
         */
         
         SOAPConnectionFactory connectFactory = SOAPConnectionFactory.newInstance();
         SOAPConnection connection = connectFactory.createConnection();
         //Send it across the wire
         URL url = new URL(endpoint);
         
         SOAPMessage response = connection.call(soapMessage, url);
         
         /*URLConnection conn = url.openConnection();
         conn.setDoOutput(true); 
         marshaller.marshal(soapRequest, conn.getOutputStream());
         
         JAXBElement<?> result = (JAXBElement<?>) unmarshaller.unmarshal(conn.getInputStream()); 
         Envelope resultEnvelope = (Envelope) result.getValue();
         
         JAXBElement<?> samlResponse = (JAXBElement<?>) resultEnvelope.getBody().getAny().get(0);
         Object response = samlResponse.getValue();
         if(response instanceof Fault)
         {
            Fault fault = (Fault) response;
            return new Result(null,fault); 
         }*/
         
         NodeList nl = response.getSOAPBody().getChildNodes();
         Node node = null;
         
         int length = nl != null ? nl.getLength() : 0;
         for( int i = 0; i < length; i++ )
         {
            Node n = nl.item(i); 
            String localName = n.getLocalName();
            if( localName.contains( JBossSAMLConstants.RESPONSE.get() ))
            {
               node = n;
               break;
            }
         }
         if( node == null )
            throw new RuntimeException( "Did not find Response node" );
         

         XMLEventReader xmlEventReader = StaxParserUtil.getXMLEventReader( DocumentUtil.getNodeAsStream( node ));
         SAMLResponseParser samlResponseParser = new SAMLResponseParser();
         ResponseType responseType = (ResponseType) samlResponseParser.parse(xmlEventReader);
         
         //ResponseType responseType = (ResponseType) response;
         AssertionType at = (AssertionType) responseType.getAssertions().get(0).getAssertion();
         XACMLAuthzDecisionStatementType xst = (XACMLAuthzDecisionStatementType) at.getStatements().iterator().next();
         ResultType rt = xst.getResponse().getResult().get(0);
         DecisionType dt = rt.getDecision(); 
         
         return new Result(dt,null);
      } 
      catch (IOException e)
      {
         throw new ProcessingException(e);
      }
      catch (ConfigurationException e)
      {
         throw new ProcessingException(e);
      }
   }
   /*
   private Envelope createEnvelope(JAXBElement<?> jaxbElement)
   {
      Envelope envelope = SOAPFactory.getObjectFactory().createEnvelope();
      Body body = SOAPFactory.getObjectFactory().createBody();
      body.getAny().add(jaxbElement); 
      envelope.setBody(body);
      return envelope;
   } */
   
   public static class Result
   {
      private Fault fault = null; 
      private DecisionType decisionType;
      
      Result(DecisionType decision, Fault fault)
      {
         this.decisionType = decision;
         this.fault = fault;
      }
      
      public boolean isResponseAvailable()
      {
         return decisionType != null;
      }
      
      public boolean isFault()
      {
         return fault != null;
      }
      
      public DecisionType getDecision()
      {
         return decisionType;
      }
      
      public Fault getFault()
      {
         return fault;
      }
      
      public boolean isPermit()
      {
         return decisionType == DecisionType.PERMIT;
      }
      
      public boolean isDeny()
      {
         return decisionType == DecisionType.DENY;
      }
   }
}