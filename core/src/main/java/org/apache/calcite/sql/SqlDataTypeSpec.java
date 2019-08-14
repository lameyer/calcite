/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.calcite.sql;

import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.util.SqlVisitor;
import org.apache.calcite.sql.validate.SqlMonotonicity;
import org.apache.calcite.sql.validate.SqlValidator;
import org.apache.calcite.sql.validate.SqlValidatorScope;
import org.apache.calcite.util.Litmus;
import org.apache.calcite.util.Util;

import java.util.Objects;
import java.util.TimeZone;

import static org.apache.calcite.util.Static.RESOURCE;

/**
 * Represents a SQL data type specification in a parse tree.
 *
 * <p>A <code>SqlDataTypeSpec</code> is immutable; once created, you cannot
 * change any of the fields.</p>
 *
 * <p>todo: This should really be a subtype of {@link SqlCall}.</p>
 *
 * <p>we support complex type expressions
 * like:</p>
 *
 * <blockquote><code>ROW(<br>
 *   foo NUMBER(5, 2) NOT NULL,<br>
 *   rec ROW(b BOOLEAN, i MyUDT NOT NULL))</code></blockquote>
 *
 * <p>Internally we use {@link SqlRowTypeNameSpec} to specify row data type name.
 *
 * <p>We support simple data types like CHAR, VARCHAR and DOUBLE,
 * with optional precision and scale.</p>
 *
 * <p>Internally we use {@link SqlBasicTypeNameSpec} to specify basic sql data type name.
 */
public class SqlDataTypeSpec extends SqlNode {
  //~ Instance fields --------------------------------------------------------

  private final SqlIdentifier collectionsTypeName;
  private final SqlTypeNameSpec typeNameSpec;
  private final SqlTypeNameSpec baseTypeName;
  private final TimeZone timeZone;

  /** Whether data type is allows nulls.
   *
   * <p>Nullable is nullable! Null means "not specified". E.g.
   * {@code CAST(x AS INTEGER)} preserves has the same nullability as {@code x}.
   */
  private Boolean nullable;

  //~ Constructors -----------------------------------------------------------

  /**
   * Creates a type specification representing a regular, non-collection type.
   */
  public SqlDataTypeSpec(
      final SqlTypeNameSpec typeNameSpec,
      SqlParserPos pos) {
    this(null, typeNameSpec, null, null, pos);
  }

  /**
   * Creates a type specification representing a regular, non-collection type.
   */
  public SqlDataTypeSpec(
      final SqlTypeNameSpec typeNameSpec,
      TimeZone timeZone,
      SqlParserPos pos) {
    this(null, typeNameSpec, timeZone, null, pos);
  }

  /**
   * Creates a type specification representing a collection type.
   */
  public SqlDataTypeSpec(
      SqlIdentifier collectionsTypeName,
      SqlTypeNameSpec typeNameSpec,
      SqlParserPos pos) {
    this(collectionsTypeName, typeNameSpec, null, null, pos);
  }

  /**
   * Creates a type specification that has no base type.
   */
  public SqlDataTypeSpec(
      SqlIdentifier collectionsTypeName,
      SqlTypeNameSpec typeName,
      TimeZone timeZone,
      Boolean nullable,
      SqlParserPos pos) {
    this(collectionsTypeName, typeName, typeName, timeZone, nullable, pos);
  }

  /**
   * Creates a type specification.
   */
  public SqlDataTypeSpec(
      SqlIdentifier collectionsTypeName,
      SqlTypeNameSpec typeNameSpec,
      SqlTypeNameSpec baseTypeName,
      TimeZone timeZone,
      Boolean nullable,
      SqlParserPos pos) {
    super(pos);
    this.collectionsTypeName = collectionsTypeName;
    this.typeNameSpec = typeNameSpec;
    this.baseTypeName = baseTypeName;
    this.timeZone = timeZone;
    this.nullable = nullable;
  }

  //~ Methods ----------------------------------------------------------------

  public SqlNode clone(SqlParserPos pos) {
    return (collectionsTypeName != null)
        ? new SqlDataTypeSpec(collectionsTypeName, typeNameSpec, pos)
        : new SqlDataTypeSpec(typeNameSpec, timeZone, pos);
  }

  public SqlMonotonicity getMonotonicity(SqlValidatorScope scope) {
    return SqlMonotonicity.CONSTANT;
  }

  public SqlIdentifier getCollectionsTypeName() {
    return collectionsTypeName;
  }

  public SqlIdentifier getTypeName() {
    return typeNameSpec.getTypeName();
  }

  public SqlTypeNameSpec getTypeNameSpec() {
    return typeNameSpec;
  }

  public TimeZone getTimeZone() {
    return timeZone;
  }

  public Boolean getNullable() {
    return nullable;
  }

  /** Returns a copy of this data type specification with a given
   * nullability. */
  public SqlDataTypeSpec withNullable(Boolean nullable) {
    if (Objects.equals(nullable, this.nullable)) {
      return this;
    }
    return new SqlDataTypeSpec(collectionsTypeName, typeNameSpec, timeZone,
        nullable, getParserPosition());
  }

  /**
   * Returns a new SqlDataTypeSpec corresponding to the component type if the
   * type spec is a collections type spec.<br>
   * Collection types are <code>ARRAY</code> and <code>MULTISET</code>.
   */
  public SqlDataTypeSpec getComponentTypeSpec() {
    assert getCollectionsTypeName() != null;
    return new SqlDataTypeSpec(
        typeNameSpec,
        timeZone,
        getParserPosition());
  }

  public void unparse(SqlWriter writer, int leftPrec, int rightPrec) {
    typeNameSpec.unparse(writer, leftPrec, rightPrec);
    // collection type can have elements as builtin sql type and UDT.
    if (collectionsTypeName != null) {
      writer.keyword(collectionsTypeName.getSimple());
    }
  }

  public void validate(SqlValidator validator, SqlValidatorScope scope) {
    validator.validateDataType(this);
  }

  public <R> R accept(SqlVisitor<R> visitor) {
    return visitor.visit(this);
  }

  public boolean equalsDeep(SqlNode node, Litmus litmus) {
    if (!(node instanceof SqlDataTypeSpec)) {
      return litmus.fail("{} != {}", this, node);
    }
    SqlDataTypeSpec that = (SqlDataTypeSpec) node;
    if (!Objects.equals(this.timeZone, that.timeZone)) {
      return litmus.fail("{} != {}", this, node);
    }
    if (!SqlNode.equalDeep(
        this.collectionsTypeName,
        that.collectionsTypeName, litmus)) {
      return litmus.fail(null);
    }
    if (!this.typeNameSpec.equalsDeep(that.typeNameSpec, litmus)) {
      return litmus.fail(null);
    }
    return litmus.succeed();
  }

  /**
   * Throws an error if the type is not found.
   */
  public RelDataType deriveType(SqlValidator validator) {
    // validate collection type name first.
    if (null != collectionsTypeName) {
      final String collectionName = collectionsTypeName.getSimple();
      if (SqlTypeName.get(collectionName) == null) {
        throw validator.newValidationError(this,
            RESOURCE.unknownDatatypeName(collectionName));
      }
    }
    RelDataTypeFactory typeFactory = validator.getTypeFactory();
    RelDataType type = deriveType(typeFactory);
    if (type == null) {
      // the type is a UDT.
      type = validator.getValidatedNodeType(typeNameSpec.getTypeName());
      if (null != collectionsTypeName) {
        type = createCollectionType(type, typeFactory);
      }
    }
    return type;
  }

  /**
   * Does not throw an error if the type is not built-in.
   */
  public RelDataType deriveType(RelDataTypeFactory typeFactory) {
    return deriveType(typeFactory, false);
  }

  /**
   * Converts this type specification to a {@link RelDataType}.
   *
   * <p>Does not throw an error if the type is not built-in.
   *
   * @param nullable Whether the type is nullable if the type specification
   *                 does not explicitly state
   */
  public RelDataType deriveType(RelDataTypeFactory typeFactory,
      boolean nullable) {
    RelDataType type;
    type = createTypeFromTypeNameSpec(typeFactory, typeNameSpec);
    if (type == null) {
      // This is definitely not a builtin data type, returns null.
      return null;
    }

    if (null != collectionsTypeName) {
      type = createCollectionType(type, typeFactory);
    }

    if (this.nullable != null) {
      nullable = this.nullable;
    }
    type = typeFactory.createTypeWithNullability(type, nullable);

    return type;
  }

  //~ Tools ------------------------------------------------------------------

  /**
   * Create collection data type.
   * @param elementType Type of the collection element.
   * @param typeFactory Type factory.
   * @return The collection data type, or throw exception if the collection
   *         type name does not belong to {@code SqlTypeName} enumerations.
   */
  private RelDataType createCollectionType(RelDataType elementType,
      RelDataTypeFactory typeFactory) {
    final String collectionName = collectionsTypeName.getSimple();
    final SqlTypeName collectionsSqlTypeName =
        Objects.requireNonNull(SqlTypeName.get(collectionName),
            collectionName);

    switch (collectionsSqlTypeName) {
    case MULTISET:
      return typeFactory.createMultisetType(elementType, -1);
    case ARRAY:
      return typeFactory.createArrayType(elementType, -1);

    default:
      throw Util.unexpected(collectionsSqlTypeName);
    }
  }

  /**
   * Create type from the type name specification directly.
   * @param typeFactory type factory.
   * @return the type.
   */
  private RelDataType createTypeFromTypeNameSpec(
      RelDataTypeFactory typeFactory,
      SqlTypeNameSpec typeNameSpec) {
    return typeNameSpec.deriveType(typeFactory);
  }
}

// End SqlDataTypeSpec.java
