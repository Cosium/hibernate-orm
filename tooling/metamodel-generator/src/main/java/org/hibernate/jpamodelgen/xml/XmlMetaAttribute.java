/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2010, Red Hat, Inc. and/or its affiliates or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat, Inc.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.jpamodelgen.xml;

import org.hibernate.jpamodelgen.model.MetaAttribute;
import org.hibernate.jpamodelgen.model.MetaEntity;
import org.hibernate.jpamodelgen.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Hardy Ferentschik
 */
public abstract class XmlMetaAttribute implements MetaAttribute {

	private final XmlMetaEntity hostingEntity;
	private final String propertyName;
	private final String type;

	XmlMetaAttribute(XmlMetaEntity parent, String propertyName, String type) {
		this.hostingEntity = parent;
		this.propertyName = propertyName;
		this.type = type;
	}

	protected String getAttributeDeclarationString() {
		return "public static volatile " + hostingEntity.importType( getMetaType() )
				+ "<" + hostingEntity.importType( hostingEntity.getQualifiedName() )
				+ ", " + hostingEntity.importType( getTypeDeclaration() )
				+ "> " + getPropertyName() + ";";
	}

	protected String getAttributeNameDeclarationString(){
		return new StringBuilder().append("public static final ")
				.append(hostingEntity.importType(String.class.getName()))
				.append(" ")
				.append(StringUtil.getUpperUnderscoreCaseFromLowerCamelCase(getPropertyName()))
				.append(META_ATTRIBUTE_NAME_SUFFIX)
				.append(" = ")
				.append("\"")
				.append(getPropertyName())
				.append("\"")
				.append(";")
				.toString();
	}

	@Override
	public List<String> getDeclarationStrings() {
		List<String> list = new ArrayList<String>(2);
		list.add(getAttributeDeclarationString());
		list.add(getAttributeNameDeclarationString());
		return list;
	}

	public String getPropertyName() {
		return propertyName;
	}

	public String getTypeDeclaration() {
		return type;
	}

	public MetaEntity getHostingEntity() {
		return hostingEntity;
	}

	@Override
	public abstract String getMetaType();

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append( "XmlMetaAttribute" );
		sb.append( "{propertyName='" ).append( propertyName ).append( '\'' );
		sb.append( ", type='" ).append( type ).append( '\'' );
		sb.append( '}' );
		return sb.toString();
	}
}
