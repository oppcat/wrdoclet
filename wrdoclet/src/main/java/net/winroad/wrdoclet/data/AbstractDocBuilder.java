package net.winroad.wrdoclet.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import net.winroad.wrdoclet.taglets.WRMemoTaglet;
import net.winroad.wrdoclet.taglets.WROccursTaglet;
import net.winroad.wrdoclet.taglets.WRReturnCodeTaglet;
import net.winroad.wrdoclet.taglets.WRTagTaglet;

import com.sun.javadoc.AnnotationDesc;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.ParameterizedType;
import com.sun.javadoc.Tag;
import com.sun.javadoc.Type;
import com.sun.tools.doclets.internal.toolkit.Configuration;
import com.sun.tools.doclets.internal.toolkit.util.Util;

public abstract class AbstractDocBuilder {
	protected WRDoc wrDoc;

	protected Map<String, Set<MethodDoc>> taggedOpenAPIMethods = new HashMap<String, Set<MethodDoc>>();

	public AbstractDocBuilder(WRDoc wrDoc) {
		this.wrDoc = wrDoc;
	}

	public WRDoc getWrDoc() {
		return wrDoc;
	}

	public void setWrDoc(WRDoc wrDoc) {
		this.wrDoc = wrDoc;
	}

	public Map<String, Set<MethodDoc>> getTaggedOpenAPIMethods() {
		return taggedOpenAPIMethods;
	}

	public void setTaggedOpenAPIMethods(
			Map<String, Set<MethodDoc>> taggedOpenAPIMethods) {
		this.taggedOpenAPIMethods = taggedOpenAPIMethods;
	}

	public void buildWRDoc() {
		this.processOpenAPIClasses(
				this.wrDoc.getConfiguration().root.classes(),
				this.wrDoc.getConfiguration());
		this.buildOpenAPIs(this.wrDoc.getConfiguration());
	}

	protected abstract void processOpenAPIClasses(ClassDoc[] classDocs,
			Configuration configuration);

	protected void processOpenAPIMethod(MethodDoc methodDoc,
			Configuration configuration) {
		if ((configuration.nodeprecated && Util.isDeprecated(methodDoc))
				|| !isOpenAPIMethod(methodDoc)) {
			return;
		}

		Tag[] methodTagArray = methodDoc.tags(WRTagTaglet.NAME);
		if (methodTagArray.length == 0) {
			String tag = WRTagTaglet.DEFAULT_TAG_NAME;
			this.wrDoc.getWRTags().add(tag);
			if (!this.taggedOpenAPIMethods.containsKey(tag)) {
				this.taggedOpenAPIMethods.put(tag, new HashSet<MethodDoc>());
			}
			this.taggedOpenAPIMethods.get(tag).add(methodDoc);
		} else {
			for (int i = 0; i < methodTagArray.length; i++) {
				Set<String> methodTags = WRTagTaglet
						.getTagSet(methodTagArray[i].text());
				this.wrDoc.getWRTags().addAll(methodTags);
				for (Iterator<String> iter = methodTags.iterator(); iter
						.hasNext();) {
					String tag = iter.next();
					if (!this.taggedOpenAPIMethods.containsKey(tag)) {
						this.taggedOpenAPIMethods.put(tag,
								new HashSet<MethodDoc>());
					}
					this.taggedOpenAPIMethods.get(tag).add(methodDoc);
				}
			}
		}
	}

	protected void buildOpenAPIs(Configuration configuration) {
		Set<Entry<String, Set<MethodDoc>>> methods = this.taggedOpenAPIMethods
				.entrySet();
		for (Iterator<Entry<String, Set<MethodDoc>>> tagMthIter = methods
				.iterator(); tagMthIter.hasNext();) {
			Entry<String, Set<MethodDoc>> kv = tagMthIter.next();
			String tagName = kv.getKey();
			if (!this.wrDoc.getTaggedOpenAPIs().containsKey(tagName)) {
				this.wrDoc.getTaggedOpenAPIs().put(tagName,
						new LinkedList<OpenAPI>());
			}
			Set<MethodDoc> methodDocSet = kv.getValue();
			for (Iterator<MethodDoc> mthIter = methodDocSet.iterator(); mthIter
					.hasNext();) {
				MethodDoc methodDoc = mthIter.next();
				OpenAPI openAPI = new OpenAPI();
				openAPI.setDescription(methodDoc.commentText());
				openAPI.setModificationHistory(this
						.getModificationHistory(methodDoc));
				openAPI.setRequestMapping(this.parseRequestMapping(methodDoc));
				openAPI.addInParameters(this.getInputParams(methodDoc));
				openAPI.setOutParameter(this.getOutputParam(methodDoc));
				openAPI.setReturnCode(this.getReturnCode(methodDoc));
				this.wrDoc.getTaggedOpenAPIs().get(tagName).add(openAPI);
			}
		}
	}

	protected abstract boolean isOpenAPIMethod(MethodDoc methodDoc);

	protected abstract RequestMapping parseRequestMapping(MethodDoc methodDoc);

	protected abstract APIParameter getOutputParam(MethodDoc methodDoc);

	protected abstract List<APIParameter> getInputParams(MethodDoc methodDoc);

	protected boolean isClassDocAnnotatedWith(ClassDoc classDoc,
			String annotation) {
		AnnotationDesc[] annotations = classDoc.annotations();
		for (int i = 0; i < annotations.length; i++) {
			if (annotations[i].annotationType().name().equals(annotation)) {
				return true;
			}
		}
		return false;
	}

	/*
	 * get the modification history of the class.
	 */
	protected ModificationHistory getModificationHistory(Type type) {
		ModificationHistory history = new ModificationHistory();
		ClassDoc classDoc = this.wrDoc.getConfiguration().root.classNamed(type
				.qualifiedTypeName());
		if (classDoc != null) {
			LinkedList<ModificationRecord> list = this
					.getModificationRecords(classDoc);
			history.AddModificationRecords(list);
		}
		return history;
	}

	/*
	 * get the modification history of the method.
	 */
	protected ModificationHistory getModificationHistory(MethodDoc methodDoc) {
		ModificationHistory history = new ModificationHistory();
		history.AddModificationRecords(this.parseModificationRecords(methodDoc
				.tags()));
		return history;
	}

	/*
	 * get the modification records of the class.
	 */
	protected LinkedList<ModificationRecord> getModificationRecords(
			ClassDoc classDoc) {
		ClassDoc superClass = classDoc.superclass();
		if (superClass == null) {
			return new LinkedList<ModificationRecord>();
		}
		LinkedList<ModificationRecord> result = this
				.getModificationRecords(superClass);
		result.addAll(this.parseModificationRecords(classDoc.tags()));
		return result;
	}

	/*
	 * Parse tags to get modification records.
	 */
	protected LinkedList<ModificationRecord> parseModificationRecords(Tag[] tags) {
		LinkedList<ModificationRecord> result = new LinkedList<ModificationRecord>();
		for (int i = 0; i < tags.length; i++) {
			if ("@author".equalsIgnoreCase(tags[i].name())) {
				ModificationRecord record = new ModificationRecord();
				record.setModifier(tags[i].text());

				if (i + 1 < tags.length) {
					if ("@version".equalsIgnoreCase(tags[i + 1].name())) {
						record.setVersion(tags[i + 1].text());
						if (i + 2 < tags.length
								&& ("@" + WRMemoTaglet.NAME)
										.equalsIgnoreCase(tags[i + 2].name())) {
							record.setMemo(tags[i + 2].text());
						}
					} else if (("@" + WRMemoTaglet.NAME)
							.equalsIgnoreCase(tags[i + 1].name())) {
						record.setMemo(tags[i + 1].text());
					}
				}
				result.add(record);
			}
		}

		return result;
	}

	protected String getReturnCode(MethodDoc methodDoc) {
		Tag[] tags = methodDoc.tags(WRReturnCodeTaglet.NAME);
		return WRReturnCodeTaglet.concat(tags);
	}

	protected List<APIParameter> getFields(ClassDoc classDoc,
			ParameterType paramType) {
		List<APIParameter> result = null;
		ClassDoc superClassDoc = classDoc.superclass();
		if (superClassDoc != null
				&& !"java.lang.Object"
						.equals(superClassDoc.qualifiedTypeName())
				&& !"java.lang.Enum".equals(superClassDoc.qualifiedTypeName())) {
			result = this.getFields(superClassDoc, paramType);
		} else {
			result = new LinkedList<APIParameter>();
		}

		FieldDoc[] fieldDocs = classDoc.fields();
		for (FieldDoc fieldDoc : fieldDocs) {
			if (fieldDoc.isPublic() && !fieldDoc.isStatic()) {
				APIParameter param = new APIParameter();
				param.setName(fieldDoc.name());
				this.processType(paramType, param, fieldDoc.type());
				param.setDescription(fieldDoc.commentText());
				param.setHistory(new ModificationHistory(this
						.parseModificationRecords(fieldDoc.tags())));
				param.setParameterOccurs(this.parseParameterOccurs(fieldDoc
						.tags(WROccursTaglet.NAME)));
				result.add(param);
			}
		}

		MethodDoc[] methodDocs = classDoc.methods(false);
		for (MethodDoc methodDoc : methodDocs) {
			if ((paramType == ParameterType.Response && this
					.isGetterMethod(methodDoc))
					|| (paramType == ParameterType.Request && this
							.isSetterMethod(methodDoc))) {
				APIParameter param = new APIParameter();
				param.setName(this.getFieldNameOfAccesser(methodDoc.name()));
				Type typeToProcess = null;
				if (paramType == ParameterType.Request) {
					// set method only has one parameter.
					typeToProcess = methodDoc.parameters()[0].type();
				} else {
					typeToProcess = methodDoc.returnType();
				}
				processType(paramType, param, typeToProcess);
				param.setHistory(new ModificationHistory(this
						.parseModificationRecords(methodDoc.tags())));
				param.setDescription(methodDoc.commentText());
				param.setParameterOccurs(this.parseParameterOccurs(methodDoc
						.tags(WROccursTaglet.NAME)));
				result.add(param);
			}
		}
		return result;
	}

	protected void processType(ParameterType paramType, APIParameter param,
			Type typeToProcess) {
		if (!typeToProcess.isPrimitive()
				&& !"java.lang.String".equalsIgnoreCase(typeToProcess
						.qualifiedTypeName())) {
			ParameterizedType pt = typeToProcess.asParameterizedType();
			if (pt != null && pt.typeArguments().length > 0) {
				StringBuilder strBuilder = new StringBuilder();
				strBuilder.append(typeToProcess.qualifiedTypeName());
				strBuilder.append("<");
				for (Type arg : pt.typeArguments()) {
					strBuilder.append(arg.simpleTypeName());
					strBuilder.append(",");
					APIParameter tmp = new APIParameter();
					tmp.setName(arg.simpleTypeName());
					tmp.setType(arg.qualifiedTypeName());
					tmp.setDescription("");
					tmp.setParentTypeArgument(true);
					tmp.setFields(this.getFields(arg, paramType));
					param.appendField(tmp);
				}
				int len = strBuilder.length();
				// trim the last ","
				strBuilder.deleteCharAt(len - 1);
				strBuilder.append(">");
				param.setType(strBuilder.toString());
			} else {
				param.setType(typeToProcess.qualifiedTypeName());
				param.setFields(this.getFields(typeToProcess, paramType));
			}
		} else {
			param.setType(typeToProcess.qualifiedTypeName());
		}

		// handle enum to output enum values into doc
		if (typeToProcess.asClassDoc() != null) {
			ClassDoc superClass = typeToProcess.asClassDoc().superclass();
			if (superClass != null
					&& "java.lang.Enum".equals(superClass.qualifiedTypeName())) {
				FieldDoc[] enumConstants = typeToProcess.asClassDoc()
						.enumConstants();
				StringBuilder strBuilder = new StringBuilder();
				strBuilder.append("Enum[");
				for (FieldDoc enumConstant : enumConstants) {
					strBuilder.append(enumConstant.name());
					strBuilder.append(",");
				}
				int len = strBuilder.length();
				// trim the last ","
				strBuilder.deleteCharAt(len - 1);
				strBuilder.append("]");
				param.setType(strBuilder.toString());
			}
		}
	}

	/*
	 * Parse the ParameterOccurs from the tags.
	 */
	protected ParameterOccurs parseParameterOccurs(Tag[] tags) {
		for (int i = 0; i < tags.length; i++) {
			if (("@" + WROccursTaglet.NAME).equalsIgnoreCase(tags[i].name())) {
				if (WROccursTaglet.REQUIRED.equalsIgnoreCase(tags[i].text())) {
					return ParameterOccurs.REQUIRED;
				} else if (WROccursTaglet.OPTIONAL.equalsIgnoreCase(tags[i]
						.text())) {
					return ParameterOccurs.OPTIONAL;
				} else if (WROccursTaglet.DEPENDS.equalsIgnoreCase(tags[i]
						.text())) {
					return ParameterOccurs.DEPENDS;
				} else {
					// TODO: WARNING in this case
				}
			}
		}
		return null;
	}

	/*
	 * is the method a getter method of a field.
	 */
	protected boolean isGetterMethod(MethodDoc methodDoc) {
		if (methodDoc.parameters() != null
				&& methodDoc.parameters().length == 0
				&& (!"boolean".equalsIgnoreCase(methodDoc.returnType()
						.qualifiedTypeName()) && methodDoc.name().matches(
						"^get[A-Z].+"))
				|| (("boolean".equalsIgnoreCase(methodDoc.returnType()
						.qualifiedTypeName()) && methodDoc.name().matches(
						"^is[A-Z].+")))) {
			return true;
		}
		return false;
	}

	/*
	 * is the method a setter method of a field.
	 */
	protected boolean isSetterMethod(MethodDoc methodDoc) {
		if (methodDoc.parameters() != null
				&& methodDoc.parameters().length == 1
				&& methodDoc.name().matches("^set[A-Z].+")) {
			return true;
		}
		return false;
	}

	protected List<APIParameter> getFields(Type type, ParameterType paramType) {
		ClassDoc classDoc = this.wrDoc.getConfiguration().root.classNamed(type
				.qualifiedTypeName());
		if (classDoc != null) {
			return this.getFields(classDoc, paramType);
		}
		return null;
	}

	/*
	 * get the field name which the getter or setter method to access. NOTE: the
	 * getter or setter method name should follow the naming convention.
	 */
	protected String getFieldNameOfAccesser(String methodName) {
		if (methodName.startsWith("get")) {
			return net.winroad.wrdoclet.utils.Util.uncapitalize(methodName
					.replaceFirst("get", ""));
		} else if (methodName.startsWith("set")) {
			return net.winroad.wrdoclet.utils.Util.uncapitalize(methodName
					.replaceFirst("set", ""));
		} else {
			return net.winroad.wrdoclet.utils.Util.uncapitalize(methodName
					.replaceFirst("is", ""));
		}
	}

}