/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.dialect;

import org.hibernate.type.StandardBasicTypes;

public class MariaDB10Dialect extends MariaDB53Dialect {

	public MariaDB10Dialect() {
		super();
	}

	@Override
	public boolean supportsIfExistsBeforeConstraintName() {
		return true;
	}
}
