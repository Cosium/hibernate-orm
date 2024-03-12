/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.tree.select;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.query.criteria.JpaCriteriaQuery;
import org.hibernate.query.criteria.JpaExpression;
import org.hibernate.query.criteria.JpaSelection;
import org.hibernate.query.sqm.FetchClauseType;
import org.hibernate.query.sqm.NodeBuilder;
import org.hibernate.query.sqm.SemanticQueryWalker;
import org.hibernate.query.sqm.SqmQuerySource;
import org.hibernate.query.sqm.internal.SqmUtil;
import org.hibernate.query.sqm.tree.SqmCopyContext;
import org.hibernate.query.sqm.tree.SqmStatement;
import org.hibernate.query.sqm.tree.cte.SqmCteStatement;
import org.hibernate.query.sqm.tree.expression.ValueBindJpaCriteriaParameter;
import org.hibernate.query.sqm.tree.expression.SqmParameter;
import org.hibernate.query.sqm.tree.expression.SqmStar;
import org.hibernate.query.sqm.tree.expression.ValueBindJpaCriteriaParameter;
import org.hibernate.query.sqm.tree.from.SqmFromClause;
import org.hibernate.query.sqm.tree.predicate.SqmPredicate;
import org.hibernate.query.sqm.tree.from.SqmRoot;

import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Selection;
import jakarta.persistence.metamodel.EntityType;

import static org.hibernate.query.sqm.spi.SqmCreationHelper.combinePredicates;
import static org.hibernate.query.sqm.SqmQuerySource.CRITERIA;
import static org.hibernate.query.sqm.tree.SqmCopyContext.noParamCopyContext;
import static org.hibernate.query.sqm.tree.jpa.ParameterCollector.collectParameters;

/**
 * @author Steve Ebersole
 */
public class SqmSelectStatement<T> extends AbstractSqmSelectQuery<T> implements JpaCriteriaQuery<T>, SqmStatement<T>,
		org.hibernate.query.sqm.internal.ParameterCollector {
	private final SqmQuerySource querySource;

	private Set<SqmParameter<?>> parameters;

	public SqmSelectStatement(NodeBuilder nodeBuilder) {
		this( SqmQuerySource.HQL, nodeBuilder );
	}

	public SqmSelectStatement(SqmQuerySource querySource, NodeBuilder nodeBuilder) {
		super( null, nodeBuilder );
		this.querySource = querySource;
	}

	public SqmSelectStatement(Class<T> resultJavaType, SqmQuerySource querySource, NodeBuilder nodeBuilder) {
		super( resultJavaType, nodeBuilder );
		this.querySource = querySource;
	}

	public SqmSelectStatement(
			SqmQueryPart<T> queryPart,
			Class<T> resultType,
			SqmQuerySource querySource,
			NodeBuilder builder) {
		super( queryPart, resultType, builder );
		this.querySource = querySource;
	}

	public SqmSelectStatement(
			SqmQueryPart<T> queryPart,
			Class<T> resultType,
			Map<String, SqmCteStatement<?>> cteStatements,
			SqmQuerySource querySource,
			NodeBuilder builder) {
		super( queryPart, cteStatements, resultType, builder );
		this.querySource = querySource;
	}

	/**
	 * @implNote This form is used from the criteria query API.
	 */
	public SqmSelectStatement(Class<T> resultJavaType, NodeBuilder nodeBuilder) {
		super( resultJavaType, nodeBuilder );
		this.querySource = CRITERIA;
		getQuerySpec().setSelectClause( new SqmSelectClause( false, nodeBuilder ) );
		getQuerySpec().setFromClause( new SqmFromClause() );
	}

	/**
	 * @implNote This form is used when transforming HQL to criteria.
	 *           All it does is change the SqmQuerySource to CRITERIA
	 *           in order to allow correct parameter handing.
	 */
	public SqmSelectStatement(SqmSelectStatement<T> original) {
		super( original.getQueryPart(), original.getCteStatementMap(), original.getResultType(), original.nodeBuilder() );
		this.querySource = CRITERIA;
	}

	private SqmSelectStatement(
			NodeBuilder builder,
			Map<String, SqmCteStatement<?>> cteStatements,
			Class<T> resultType,
			SqmQuerySource querySource,
			Set<SqmParameter<?>> parameters) {
		super( builder, cteStatements, resultType );
		this.querySource = querySource;
		this.parameters = parameters;
	}

	@Override
	public SqmSelectStatement<T> copy(SqmCopyContext context) {
		final SqmSelectStatement<T> existing = context.getCopy( this );
		if ( existing != null ) {
			return existing;
		}
		return createCopy( context, getResultType() );
	}

	private <X> SqmSelectStatement<X> createCopy(SqmCopyContext context, Class<X> resultType) {
		final Set<SqmParameter<?>> parameters;
		if ( this.parameters == null ) {
			parameters = null;
		}
		else {
			parameters = new LinkedHashSet<>( this.parameters.size() );
			for ( SqmParameter<?> parameter : this.parameters ) {
				parameters.add( parameter.copy( context ) );
			}
		}
		//noinspection unchecked
		final SqmSelectStatement<X> statement = (SqmSelectStatement<X>) context.registerCopy(
				this,
				new SqmSelectStatement<>(
						nodeBuilder(),
						copyCteStatements( context ),
						resultType,
						getQuerySource(),
						parameters
				)
		);
		//noinspection unchecked
		statement.setQueryPart( (SqmQueryPart<X>) getQueryPart().copy( context ) );
		return statement;
	}

	@Override
	public SqmQuerySource getQuerySource() {
		return querySource;
	}

	@Override
	public SqmQuerySpec<T> getQuerySpec() {
		if ( querySource == CRITERIA ) {
			final SqmQueryPart<T> queryPart = getQueryPart();
			if ( queryPart instanceof SqmQuerySpec<?> ) {
				return (SqmQuerySpec<T>) queryPart;
			}
			throw new IllegalStateException(
					"Query group can't be treated as query spec. Use JpaSelectCriteria#getQueryPart to access query group details"
			);
		}
		else {
			return super.getQuerySpec();
		}
	}

	public boolean producesUniqueResults() {
		return producesUniqueResults( getQueryPart() );
	}

	private boolean producesUniqueResults(SqmQueryPart<?> queryPart) {
		if ( queryPart instanceof SqmQuerySpec<?> ) {
			return ( (SqmQuerySpec<?>) queryPart ).producesUniqueResults();
		}
		else {
			// For query groups we have to assume that duplicates are possible
			return true;
		}
	}

	public boolean containsCollectionFetches() {
		return containsCollectionFetches( getQueryPart() );
	}

	private boolean containsCollectionFetches(SqmQueryPart<?> queryPart) {
		if ( queryPart instanceof SqmQuerySpec<?> ) {
			return ( (SqmQuerySpec<?>) queryPart ).containsCollectionFetches();
		}
		else {
			// We only have to check the first one
			final SqmQueryGroup<?> queryGroup = (SqmQueryGroup<?>) queryPart;
			return containsCollectionFetches( queryGroup.getQueryParts().get( 0 ) );
		}
	}

	public boolean usesDistinct() {
		return usesDistinct( getQueryPart() );
	}

	private boolean usesDistinct(SqmQueryPart<?> queryPart) {
		if ( queryPart instanceof SqmQuerySpec<?> ) {
			return ( (SqmQuerySpec<?>) queryPart ).getSelectClause().isDistinct();
		}
		else {
			// We only have to check the first one
			final SqmQueryGroup<?> queryGroup = (SqmQueryGroup<?>) queryPart;
			return usesDistinct( queryGroup.getQueryParts().get( 0 ) );
		}
	}

	@Override
	public Set<SqmParameter<?>> getSqmParameters() {
		if ( querySource == CRITERIA ) {
			assert parameters == null : "SqmSelectStatement (as Criteria) should not have collected parameters";
			return collectParameters( this );
		}

		return parameters == null ? Collections.emptySet() : Collections.unmodifiableSet( parameters );
	}

	@Override
	public ParameterResolutions resolveParameters() {
		return SqmUtil.resolveParameters( this );
	}

	@Override
	public <X> X accept(SemanticQueryWalker<X> walker) {
		return walker.visitSelectStatement( this );
	}

	@Override
	public void addParameter(SqmParameter<?> parameter) {
		if ( parameters == null ) {
			parameters = new LinkedHashSet<>();
		}

		parameters.add( parameter );
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// JPA

	@Override
	public SqmSelectStatement<T> distinct(boolean distinct) {
		return (SqmSelectStatement<T>) super.distinct( distinct );
	}

	@Override
	public Set<ParameterExpression<?>> getParameters() {
		// At this level, the number of parameters may still be growing as
		// nodes are added to the Criteria - so we re-calculate this every
		// time.
		//
		// for a "finalized" set of parameters, use `#resolveParameters` instead
		assert querySource == CRITERIA;
		return getSqmParameters().stream()
				.filter( parameterExpression -> !( parameterExpression instanceof ValueBindJpaCriteriaParameter ) )
				.collect( Collectors.toSet() );
	}

	@Override
	public <U> SqmSubQuery<U> subquery(EntityType<U> type) {
		return new SqmSubQuery<>( this, type, nodeBuilder() );
	}

	@Override
	public SqmSelectStatement<T> where(List<Predicate> restrictions) {
		//noinspection rawtypes,unchecked
		getQuerySpec().getWhereClause().applyPredicates( (List) restrictions );
		return this;
	}

	@Override
	public SqmSelectStatement<T> having(List<Predicate> restrictions) {
		//noinspection unchecked,rawtypes
		final SqmPredicate combined = combinePredicates( getQuerySpec().getHavingClausePredicate(), (List) restrictions );
		getQuerySpec().setHavingClausePredicate( combined );
		return this;
	}

	@Override
	@SuppressWarnings("unchecked")
	public SqmSelectStatement<T> select(Selection<? extends T> selection) {
		if ( nodeBuilder().isJpaQueryComplianceEnabled() ) {
			checkSelectionIsJpaCompliant( selection );
		}
		getQuerySpec().setSelection( (JpaSelection<T>) selection );
		return this;
	}

	@Override
	public SqmSelectStatement<T> multiselect(Selection<?>... selections) {
		if ( nodeBuilder().isJpaQueryComplianceEnabled() ) {
			for ( Selection<?> selection : selections ) {
				checkSelectionIsJpaCompliant( selection );
			}
		}

		final Selection<? extends T> resultSelection = getResultSelection( selections );
		getQuerySpec().getSelectClause().setSelection( (SqmSelectableNode<?>) resultSelection );
		return this;
	}

	@Override
	public SqmSelectStatement<T> multiselect(List<Selection<?>> selectionList) {
		if ( nodeBuilder().isJpaQueryComplianceEnabled() ) {
			for ( Selection<?> selection : selectionList ) {
				checkSelectionIsJpaCompliant( selection );
			}
		}
		final Selection<? extends T> resultSelection = getResultSelection( selectionList );
		getQuerySpec().getSelectClause().setSelection( (SqmSelectableNode<?>) resultSelection );
		return this;
	}

	@SuppressWarnings("unchecked")
	private Selection<? extends T> getResultSelection(List<?> selectionList) {
		final Class<T> resultType = getResultType();
		//noinspection rawtypes
		final List<? extends JpaSelection<?>> selections = (List) selectionList;
		if ( resultType == null || resultType == Object.class ) {
			switch ( selectionList.size() ) {
				case 0: {
					throw new IllegalArgumentException(
							"empty selections passed to criteria query typed as Object"
					);
				}
				case 1: {
					return (Selection<? extends T>) selectionList.get( 0 );
				}
				default: {
					return (Selection<? extends T>) nodeBuilder().array( (List) selectionList );
				}
			}
		}
		else if ( Tuple.class.isAssignableFrom( resultType ) ) {
			return (Selection<? extends T>) nodeBuilder().tuple( (List) selectionList );
		}
		else if ( resultType.isArray() ) {
			return nodeBuilder().array( resultType, selections );
		}
		else {
			return nodeBuilder().construct( resultType, selections );
		}
	}

	private void checkSelectionIsJpaCompliant(Selection<?> selection) {
		if ( selection instanceof SqmSubQuery<?> ) {
			throw new IllegalStateException(
					"The JPA specification does not support subqueries in select clauses. " +
							"Please disable the JPA query compliance if you want to use this feature." );
		}
	}

	@Override
	public SqmSelectStatement<T> orderBy(Order... orders) {
		final SqmOrderByClause sqmOrderByClause = new SqmOrderByClause( orders.length );
		for ( Order order : orders ) {
			sqmOrderByClause.addSortSpecification( (SqmSortSpecification) order );
		}
		getQueryPart().setOrderByClause( sqmOrderByClause );
		return this;
	}

	@Override
	public SqmSelectStatement<T> orderBy(List<Order> orders) {
		final SqmOrderByClause sqmOrderByClause = new SqmOrderByClause( orders.size() );
		for ( Order order : orders ) {
			sqmOrderByClause.addSortSpecification( (SqmSortSpecification) order );
		}
		getQueryPart().setOrderByClause( sqmOrderByClause );
		return this;
	}

	@Override
	public <U> SqmSubQuery<U> subquery(Class<U> type) {
		return new SqmSubQuery<>( this, type, nodeBuilder() );
	}

	@Override
	public SqmSelectStatement<T> where(Expression<Boolean> restriction) {
		return (SqmSelectStatement<T>) super.where( restriction );
	}

	@Override
	public SqmSelectStatement<T> where(Predicate... restrictions) {
		return (SqmSelectStatement<T>) super.where( restrictions );
	}

	@Override
	public SqmSelectStatement<T> groupBy(Expression<?>... expressions) {
		return (SqmSelectStatement<T>) super.groupBy( expressions );
	}

	@Override
	public SqmSelectStatement<T> groupBy(List<Expression<?>> grouping) {
		return (SqmSelectStatement<T>) super.groupBy( grouping );
	}

	@Override
	public SqmSelectStatement<T> having(Expression<Boolean> booleanExpression) {
		return (SqmSelectStatement<T>) super.having( booleanExpression );
	}

	@Override
	public SqmSelectStatement<T> having(Predicate... predicates) {
		return (SqmSelectStatement<T>) super.having( predicates );
	}

	@Override
	public JpaExpression<Number> getOffset() {
		//noinspection unchecked
		return (JpaExpression<Number>) getQueryPart().getOffset();
	}

	@Override
	public JpaCriteriaQuery<T> offset(JpaExpression<? extends Number> offset) {
		validateComplianceFetchOffset();
		getQueryPart().setOffset( offset );
		return this;
	}

	@Override
	public JpaCriteriaQuery<T> offset(Number offset) {
		validateComplianceFetchOffset();
		getQueryPart().setOffset( nodeBuilder().value( offset ) );
		return this;
	}

	@Override
	public JpaExpression<Number> getFetch() {
		//noinspection unchecked
		return (JpaExpression<Number>) getQueryPart().getFetch();
	}

	@Override
	public JpaCriteriaQuery<T> fetch(JpaExpression<? extends Number> fetch) {
		validateComplianceFetchOffset();
		getQueryPart().setFetch( fetch );
		return this;
	}

	@Override
	public JpaCriteriaQuery<T> fetch(JpaExpression<? extends Number> fetch, FetchClauseType fetchClauseType) {
		validateComplianceFetchOffset();
		getQueryPart().setFetch( fetch, fetchClauseType );
		return this;
	}

	@Override
	public JpaCriteriaQuery<T> fetch(Number fetch) {
		validateComplianceFetchOffset();
		getQueryPart().setFetch( nodeBuilder().value( fetch ) );
		return this;
	}

	@Override
	public JpaCriteriaQuery<T> fetch(Number fetch, FetchClauseType fetchClauseType) {
		validateComplianceFetchOffset();
		getQueryPart().setFetch( nodeBuilder().value( fetch ), fetchClauseType );
		return this;
	}

	@Override
	public FetchClauseType getFetchClauseType() {
		return getQueryPart().getFetchClauseType();
	}

	private void validateComplianceFetchOffset() {
		if ( nodeBuilder().isJpaQueryComplianceEnabled() ) {
			throw new IllegalStateException(
					"The JPA specification does not support the fetch or offset clause. " +
							"Please disable the JPA query compliance if you want to use this feature." );
		}
	}

	@Override
	public SqmSelectStatement<Long> createCountQuery() {
		final SqmSelectStatement<?> copy = createCopy( noParamCopyContext(), Object.class );
		final SqmQueryPart<?> queryPart = copy.getQueryPart();
		final SqmQuerySpec<?> querySpec;
		//TODO: detect queries with no 'group by', but aggregate functions
		//      in 'select' list (we don't even need to hit the database to
		//      know they return exactly one row)
		if ( queryPart.isSimpleQueryPart()
				&& !( querySpec = (SqmQuerySpec<?>) queryPart ).isDistinct()
				&& querySpec.getGroupingExpressions().isEmpty() ) {
			for ( SqmRoot<?> root : querySpec.getRootList() ) {
				root.removeLeftFetchJoins();
			}
			querySpec.getSelectClause().setSelection( nodeBuilder().count() );
			if ( querySpec.getFetch() == null && querySpec.getOffset() == null ) {
				querySpec.setOrderByClause( null );
			}

			return (SqmSelectStatement<Long>) copy;
		}
		else {
			aliasSelections( queryPart );
			final SqmSubQuery<?> subquery = new SqmSubQuery<>( copy, queryPart, null, nodeBuilder() );
			final SqmSelectStatement<Long> query = nodeBuilder().createQuery( Long.class );
			query.from( subquery );
			query.select( nodeBuilder().count() );
			return query;
		}
	}

	private <S> void aliasSelections(SqmQueryPart<S> queryPart) {
		if ( queryPart.isSimpleQueryPart() ) {
			final SqmQuerySpec<S> querySpec = queryPart.getFirstQuerySpec();
			final LinkedHashSet<JpaSelection<?>> newSelections = new LinkedHashSet<>();
			aliasSelection( querySpec.getSelection(), newSelections );
			//noinspection unchecked
			querySpec.setSelection( (JpaSelection<S>) ( newSelections.size() == 1 ?
					newSelections.iterator().next() :
					nodeBuilder().tuple( newSelections.toArray( new JpaSelection<?>[0] ) ) ) );
		}
		else {
			( (SqmQueryGroup<?>) queryPart ).getQueryParts().forEach( this::aliasSelections );
		}
	}

	private void aliasSelection(JpaSelection<?> selection, LinkedHashSet<JpaSelection<?>> newSelections) {
		if ( selection.isCompoundSelection() || selection instanceof SqmDynamicInstantiation<?> ) {
			for ( JpaSelection<?> selectionItem : selection.getSelectionItems() ) {
				aliasSelection( selectionItem, newSelections );
			}
		}
		else {
			newSelections.add( selection.alias( "c" + newSelections.size() ) );
		}
	}
}
