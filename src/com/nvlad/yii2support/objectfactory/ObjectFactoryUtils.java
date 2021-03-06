package com.nvlad.yii2support.objectfactory;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.elements.*;
import com.nvlad.yii2support.common.ClassUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

/**
 * Created by oleg on 14.03.2017.
 */
public class ObjectFactoryUtils {
    @Nullable
    static public PhpClass findClassByArray(@NotNull ArrayCreationExpression arrayCreationExpression) {
        HashMap<String, String> keys = new HashMap<>();

        for (ArrayHashElement arrayHashElement : arrayCreationExpression.getHashElements()) {
            PhpPsiElement child = arrayHashElement.getKey();
            if (child instanceof StringLiteralExpression) {
                String key = ((StringLiteralExpression) child).getContents();
                if (key.equals("class")) {
                    Project project = child.getProject();
                    PhpPsiElement value = arrayHashElement.getValue();
                    PhpClass methodRef = ClassUtils.getPhpClassUniversal(project, value);
                    if (methodRef != null) return methodRef;
                }
            }
        }

        return null;
    }

    @Nullable
    static PhpClass getPhpClassByYiiCreateObject(ArrayCreationExpression arrayCreation) {
        PhpClass phpClass = null;
        PsiElement parent = arrayCreation.getParent().getParent();
        if (parent instanceof MethodReference) {
            MethodReference method = (MethodReference) parent;
            if (method.getName() != null && method.getName().equals("createObject")) {
                PhpExpression methodClass = method.getClassReference();
                if (methodClass != null && methodClass.getName() != null && methodClass.getName().equals("Yii")) {
                    PsiElement[] pList = method.getParameters();
                    if (pList.length == 2 && ClassUtils.indexForElementInParameterList(arrayCreation) == 1) { // \Yii::createObject takes 2 paramters
                        phpClass = ClassUtils.getPhpClassUniversal(method.getProject(), (PhpPsiElement) pList[0]);
                    }
                }
            }
        }
        return phpClass;
    }

    static PhpClass getPhpClassInConfig(PsiDirectory dir, ArrayCreationExpression arrayCreation) {
        PhpClass phpClass = null;
        if (dir != null && (dir.getName().equals("config") || dir.getName().equals("src") /* for tests */)) {
            PsiElement parent = arrayCreation.getParent().getParent();
            if (parent instanceof ArrayHashElement) {
                ArrayHashElement hash = (ArrayHashElement) parent;
                PsiElement element = hash.getKey();
                if (element instanceof StringLiteralExpression) {
                    StringLiteralExpression literal = (StringLiteralExpression) element;
                    String key = literal.getContents();
                    phpClass = getStandardPhpClass(PhpIndex.getInstance(literal.getProject()), key);
                }
            }
        }
        return phpClass;
    }

    static PhpClass getPhpClassInWidget(ArrayCreationExpression arrayCreation) {
        PsiElement parent = arrayCreation.getParent().getParent();
        if (parent instanceof MethodReference) {
            MethodReference methodRef = (MethodReference) parent;
            if (methodRef.getName() != null && (methodRef.getName().equals("widget") || methodRef.getName().equals("begin"))) {
                Method method = (Method) methodRef.resolve();

                PhpExpression ref = methodRef.getClassReference();
                if (ref instanceof ClassReference && ClassUtils.indexForElementInParameterList(arrayCreation) == 0) {
                    PhpClass callingClass = (PhpClass) ((ClassReference) ref).resolve();
                    PhpClass superClass = ClassUtils.getClass(PhpIndex.getInstance(methodRef.getProject()), "\\yii\\base\\Widget");
                    if (ClassUtils.isClassInheritsOrEqual(callingClass, superClass))
                        return callingClass;
                } else if (method != null && ref instanceof MethodReference && ClassUtils.indexForElementInParameterList(arrayCreation) == 1) {
                    // This code process
                    // $form->field($model, 'username')->widget(\Class::className())
                    PhpClass callingClass = method.getContainingClass();
                    PhpClass superClass = ClassUtils.getClass(PhpIndex.getInstance(methodRef.getProject()), "yii\\widgets\\ActiveField");
                    if (ClassUtils.isClassInheritsOrEqual(callingClass, superClass)
                            && method.getParameters().length == 2 &&
                            method.getParameters()[0].getName().equals("class")) {
                        PhpPsiElement element = (PhpPsiElement) methodRef.getParameters()[0];
                        PhpClass widgetClass = ClassUtils.getPhpClassUniversal(methodRef.getProject(), element);
                        if (widgetClass != null)
                            return widgetClass;

                    }

                }

            }
        }
        return null;
    }

    static PhpClass getPhpClassInGridColumns(ArrayCreationExpression arrayCreation) {
        PsiElement parent = arrayCreation.getParent().getParent();
        if (parent instanceof ArrayCreationExpression) {
            PsiElement possibleHashElement = arrayCreation.getParent().getParent().getParent().getParent();
            if (!(possibleHashElement instanceof ArrayHashElement)) {
                return null;
            }

            PsiElement key = ((ArrayHashElement) possibleHashElement).getKey();
            if (key != null &&
                    key.getText() != null &&
                    key.getText().replace("\"", "").replace("\'", "").equals("columns")) {
                PsiElement methodRef = possibleHashElement.getParent().getParent().getParent();
                if (methodRef instanceof MethodReference) {
                    MethodReference method = (MethodReference) methodRef;
                    if (method.getClassReference() != null) {
                        PhpExpression methodClass = method.getClassReference();
                        if (!(methodClass instanceof ClassReference)) {
                            return null;
                        }

                        PhpIndex phpIndex = PhpIndex.getInstance(methodClass.getProject());
                        PhpClass callingClass = (PhpClass) ((ClassReference) methodClass).resolve();
                        if (callingClass != null && ClassUtils.isClassInheritsOrEqual(callingClass, "\\yii\\grid\\GridView", phpIndex)) {
                            return ClassUtils.getClass(phpIndex, "\\yii\\grid\\DataColumn");
                        }
                    }
                }

            }

        }
        return null;
    }

    @Nullable
    static PhpClass findClassByArrayCreation(ArrayCreationExpression arrayCreation, PsiDirectory dir) {
        if (arrayCreation == null)
            return null;
        PhpClass phpClass;
        phpClass = findClassByArray(arrayCreation);
        if (phpClass == null) {
            phpClass = getClassByInstatiation(arrayCreation);
        }
        if (phpClass == null) {
            phpClass = getPhpClassByYiiCreateObject(arrayCreation);
        }
        if (phpClass == null) {
            phpClass = getPhpClassInWidget(arrayCreation);
        }
        if (phpClass == null) {
            phpClass = getPhpClassInGridColumns(arrayCreation);
        }
        if (phpClass == null) {
            phpClass = getClassByParameterType(arrayCreation);
        }
        if (phpClass == null && arrayCreation.getParent().getParent() instanceof ArrayHashElement) {
            phpClass = getPhpClassByHash((ArrayHashElement) arrayCreation.getParent().getParent(), dir);
        }
        if (phpClass == null) {
            phpClass = getPhpClassInConfig(dir, arrayCreation);
        }
        return phpClass;
    }

    /**
     * Find class if array is parameter
     * @return Class
     */
    @Nullable
    private static PhpClass getClassByParameterType(ArrayCreationExpression arrayCreation) {
        if (arrayCreation.getParent() instanceof ParameterList) {
            int index = ClassUtils.indexForElementInParameterList(arrayCreation);
            if (index > -1) {
                PsiElement possibleMethodRef = arrayCreation.getParent().getParent();
                if (possibleMethodRef instanceof MethodReference) {
                    Method method = (Method)((MethodReference) possibleMethodRef).resolve();
                    if (method != null && method.getParameters().length > index) {
                        Parameter parameter = method.getParameters()[index];
                        PhpClass resultClass = ClassUtils.getElementType(parameter);
                        if (resultClass != null) return resultClass;
                    }
                }
            }
        }
        return null;
    }

    private static PhpClass getPhpClassByHash(ArrayHashElement hashElement, PsiDirectory dir) {
        if (hashElement.getParent() instanceof ArrayCreationExpression) {
            PhpClass phpClass = findClassByArrayCreation((ArrayCreationExpression) hashElement.getParent(), dir);
            if (phpClass == null)
                return null;
            String fieldName = hashElement.getKey() != null ? hashElement.getKey().getText() : null;
            if (fieldName == null)
                return null;
            PhpClassMember field = ClassUtils.findWritableField(phpClass, fieldName);
            if (field == null)
                return null;
            PhpClass resultClass = ClassUtils.getElementType(field);
            if (resultClass != null) return resultClass;
        }
        return null;
    }

    static PhpClass getClassByInstatiation(PhpExpression element) {

        PsiElement newElement = element.getParent().getParent();
        if (newElement instanceof NewExpression) {
            ClassReference ref = ((NewExpression) newElement).getClassReference();
            if (ref == null)
                return null;

            PsiElement possiblePhpClass = ref.resolve();
            if (!(possiblePhpClass instanceof PhpClass))
                return null;

            PhpClass phpClass = (PhpClass) possiblePhpClass;

            Method constructor = phpClass.getConstructor();
            if (constructor == null) {
                return null;
            }

            PhpClass yiiObjectClass = ClassUtils.getClass(PhpIndex.getInstance(element.getProject()), "\\yii\\base\\BaseObject");
            if (yiiObjectClass == null)
                yiiObjectClass = ClassUtils.getClass(PhpIndex.getInstance(element.getProject()), "\\yii\\base\\Object");
            if (!ClassUtils.isClassInheritsOrEqual(phpClass, yiiObjectClass))
                return null;

            Parameter[] parameterList = constructor.getParameters();
            if (parameterList.length > 0 && parameterList[0].getName().equals("config") && ClassUtils.indexForElementInParameterList(element) == 0)
                return phpClass;

        }
        return null;
    }


    static PhpClass getStandardPhpClass(PhpIndex phpIndex, String shortName) {
        switch (shortName){
            // web/Application
            case "request":  return ClassUtils.getClass(phpIndex, "\\yii\\web\\Request");
            case "response":  return ClassUtils.getClass(phpIndex, "\\yii\\web\\Response");
            case "session":  return ClassUtils.getClass(phpIndex, "\\yii\\web\\Session");
            case "user":  return ClassUtils.getClass(phpIndex, "\\yii\\web\\User");
            case "errorHandler":  return ClassUtils.getClass(phpIndex, "\\yii\\web\\ErrorHandler");
            // base/Application
            case "log":  return ClassUtils.getClass(phpIndex, "\\yii\\log\\Dispatcher");
            case "view":  return ClassUtils.getClass(phpIndex, "\\yii\\web\\View");
            case "formatter":  return ClassUtils.getClass(phpIndex, "yii\\i18n\\Formatter");
            case "i18n":  return ClassUtils.getClass(phpIndex, "yii\\i18n\\I18N");
            case "mailer":  return ClassUtils.getClass(phpIndex, "\\yii\\swiftmailer\\Mailer");
            case "urlManager":  return ClassUtils.getClass(phpIndex, "\\yii\\web\\UrlManager");
            case "assetManager":  return ClassUtils.getClass(phpIndex, "\\yii\\web\\AssetManager");
            case "security":  return ClassUtils.getClass(phpIndex, "\\yii\\base\\Security");
            // custom
            case "db": return ClassUtils.getClass(phpIndex, "\\yii\\db\\Connection");
        }
        return null;
    }

    @Nullable
    static ArrayCreationExpression getArrayCreationByVarRef(Variable value) {
        ArrayCreationExpression arrayCreation;
        PsiElement arrayDecl = value.resolve();
        if (arrayDecl != null && arrayDecl.getParent() != null && arrayDecl.getParent().getChildren().length > 1 ) {
            PsiElement psiElement = arrayDecl.getParent().getLastChild();
            if (psiElement instanceof ArrayCreationExpression)
                arrayCreation = (ArrayCreationExpression)psiElement;
            else
                return null;
        } else
            return null;
        return arrayCreation;
    }

    @Nullable
    static ArrayCreationExpression getArrayCreationByFieldRef(FieldReference value) {
        ArrayCreationExpression arrayCreation = null;
        PsiElement arrayDecl = value.resolve();
        if (arrayDecl != null && arrayDecl.getParent() != null && arrayDecl.getParent().getChildren().length > 1) {
            PsiElement psiElement = arrayDecl.getLastChild();
            if (psiElement instanceof ArrayCreationExpression) {
                arrayCreation = (ArrayCreationExpression) psiElement;
            }
        }

        return arrayCreation;
    }
}
