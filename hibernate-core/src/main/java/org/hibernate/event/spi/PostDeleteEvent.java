/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.event.spi;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.persister.entity.EntityPersister;

/**
 * Occurs after deleting an item from the datastore
 *
 * @author Gavin King
 */
public class PostDeleteEvent extends AbstractEvent {
	private final Object entity;
	private final EntityPersister persister;
	private final Object id;
	private final Object[] deletedState;

	public PostDeleteEvent(
			Object entity,
			Object id,
			Object[] deletedState,
			EntityPersister persister,
			EventSource source) {
		super(source);
		this.entity = entity;
		this.id = id;
		this.persister = persister;
		this.deletedState = deletedState;
	}

	public Object getId() {
		return id;
	}

	public EntityPersister getPersister() {
		return persister;
	}

	@Override
	public SessionFactoryImplementor getFactory() {
		return persister.getFactory();
	}

	public Object getEntity() {
		return entity;
	}

	public Object[] getDeletedState() {
		return deletedState;
	}
}
