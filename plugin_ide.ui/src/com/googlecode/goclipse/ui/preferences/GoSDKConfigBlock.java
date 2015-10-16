/*******************************************************************************
 * Copyright (c) 2015 Bruno Medeiros and other Contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Bruno Medeiros - initial API and implementation
 *******************************************************************************/
package com.googlecode.goclipse.ui.preferences;

import static melnorme.lang.tooling.ops.util.PathValidator.LocationKind.FILE_ONLY;
import static melnorme.utilbox.core.CoreUtil.array;

import java.io.File;
import java.util.List;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;

import com.googlecode.goclipse.core.GoEnvironmentPrefs;
import com.googlecode.goclipse.core.GoEnvironmentUtils;
import com.googlecode.goclipse.tooling.GoSDKLocationValidator;
import com.googlecode.goclipse.tooling.env.GoArch;
import com.googlecode.goclipse.tooling.env.GoOs;

import melnorme.lang.ide.ui.preferences.AbstractPreferencesBlockExt;
import melnorme.lang.ide.ui.preferences.common.AbstractPreferencesBlock;
import melnorme.lang.ide.ui.preferences.common.PreferencesPageContext;
import melnorme.lang.tooling.data.IValidatedField.ValidatedField;
import melnorme.lang.tooling.ops.util.LocationValidator;
import melnorme.lang.utils.ProcessUtils;
import melnorme.util.swt.SWTFactoryUtil;
import melnorme.util.swt.components.fields.ComboBoxField;
import melnorme.util.swt.components.fields.DirectoryTextField;
import melnorme.util.swt.components.fields.EnablementButtonTextField;
import melnorme.util.swt.components.fields.FileTextField;
import melnorme.utilbox.concurrency.OperationCancellation;
import melnorme.utilbox.core.CommonException;
import melnorme.utilbox.misc.ArrayUtil;

public class GoSDKConfigBlock extends AbstractPreferencesBlockExt {
	
	public final DirectoryTextField goRootField = new DirectoryTextField("GO&ROOT:");
	protected final GoSDKLocationValidator goSDKLocationValidator = new GoSDKLocationValidator();
	public final ValidatedField validatedGoRoot = new ValidatedField(goRootField, goSDKLocationValidator);
	
	protected final ComboBoxField goOSField = new ComboBoxField("G&OOS:",
		ArrayUtil.prepend("<default>", GoOs.GOOS_VALUES),
		ArrayUtil.prepend("", GoOs.GOOS_VALUES));
	protected final ComboBoxField goArchField = new ComboBoxField("GO&ARCH:", 
		array("<default>", GoArch.ARCH_AMD64, GoArch.ARCH_386,  GoArch.ARCH_ARM), 
		array(""         , GoArch.ARCH_AMD64, GoArch.ARCH_386, GoArch.ARCH_ARM));
	
	protected final FileTextField goToolPath = new FileTextField("go tool:");
	protected final FileTextField goFmtPath = new FileTextField("gofmt:");
	protected final FileTextField goDocPath = new FileTextField("godoc:");
	
	protected final EnablementButtonTextField goPathField = new GoPathField();
	
	public GoSDKConfigBlock(PreferencesPageContext prefContext) {
		super(prefContext);
		
		bindToPreference(goRootField, GoEnvironmentPrefs.GO_ROOT);
		bindToPreference(goOSField.asStringProperty(), GoEnvironmentPrefs.GO_OS);
		bindToPreference(goArchField.asStringProperty(), GoEnvironmentPrefs.GO_ARCH);
		
		bindToPreference(goToolPath, GoEnvironmentPrefs.COMPILER_PATH);
		bindToPreference(goFmtPath, GoEnvironmentPrefs.FORMATTER_PATH);
		bindToPreference(goDocPath, GoEnvironmentPrefs.DOCUMENTOR_PATH);
		
		bindToPreference(goPathField.asEffectiveValueProperty(), GoEnvironmentPrefs.GO_PATH);
		
		validation.addFieldValidation(true, goRootField, goSDKLocationValidator);
		
		validation.addFieldValidation(true, goToolPath, new LocationValidator(goToolPath.getLabelText(), FILE_ONLY));
		validation.addFieldValidation(true, goFmtPath, new LocationValidator(goFmtPath.getLabelText(), FILE_ONLY));
		validation.addFieldValidation(true, goDocPath, new LocationValidator(goDocPath.getLabelText(), FILE_ONLY));
	}
	
	/* -----------------  ----------------- */
	
	@Override
	public int getPreferredLayoutColumns() {
		return 1;
	}
	
	@Override
	protected void createContents(Composite topControl) {
		int numColumns = 3;
		Group goSDK = AbstractPreferencesBlock.createOptionsSection(topControl, "Go SDK installation:", numColumns,
			getPreferenceGroupDefaultLayout());
		
		goRootField.createComponentInlined(goSDK);
		
		goOSField.createComponentInlined(goSDK);
		goArchField.createComponentInlined(goSDK);
		
		SWTFactoryUtil.createLabel(goSDK, 
			SWT.SEPARATOR | SWT.HORIZONTAL, "",
			gdFillDefaults().span(numColumns, 1).grab(true, false).indent(0, 5).create());
		
		goToolPath.createComponentInlined(goSDK);
		goFmtPath.createComponentInlined(goSDK);
		goDocPath.createComponentInlined(goSDK);
		
		/* -----------------  ----------------- */
		
		goPathField.createComponent(topControl, getPreferenceGroupDefaultLayout());
		
		goRootField.addListener(() -> handleGoRootChange());
	}
	
	@Override
	public void setEnabled(boolean enabled) {
		goRootField.setEnabled(enabled);
		goOSField.setEnabled(enabled);
		goArchField.setEnabled(enabled);
		
		goToolPath.setEnabled(enabled);
		goFmtPath.setEnabled(enabled);
		goDocPath.setEnabled(enabled);
		goPathField.setEnabled(enabled);
	}
	
	@Override
	public void updateComponentFromInput() {
	}
	
	protected GridData getPreferenceGroupDefaultLayout() {
		return GridDataFactory.fillDefaults().grab(true, false).minSize(300, SWT.DEFAULT).create();
	}
	
	protected void handleGoRootChange() {
		IPath gorootPath = new Path(goRootField.getFieldValue());
		File binPath = gorootPath.append("bin").toFile();
		
		if(validatedGoRoot.getValidationStatusLevel().isOkStatus()) {
			
			File compilerFile = findExistingFile(binPath.toPath(), 
				GoEnvironmentUtils.getSupportedCompilerNames());
			
			goOSField.setFieldStringValue(""); 
			goArchField.setFieldStringValue(""); 
			
			setValueIfFileExists(goToolPath, compilerFile);
			
			String goFmtName = GoEnvironmentUtils.getDefaultGofmtName();
			setValueIfFileExists(goFmtPath, gorootPath.append("bin").append(goFmtName).toFile());
			
			String goDocName = GoEnvironmentUtils.getDefaultGodocName();
			setValueIfFileExists(goDocPath, gorootPath.append("bin").append(goDocName).toFile());
			
		}
	}
	
	protected void setValueIfFileExists(FileTextField fileField, File filePath) {
		if (filePath != null && filePath.exists() && filePath.isFile()) {
			fileField.setFieldValue(filePath.getAbsolutePath());
		} else {
			fileField.setFieldValue("");
		}
	}
	
	protected static File findExistingFile(java.nio.file.Path binPath, List<String> paths) {
		for (String strPath : paths) {
			java.nio.file.Path path = binPath.resolve(strPath);
			File file = path.toFile();
			if (file.exists()) {
				return file;
			}
		}
		
		return null;
	}
	
	/* -----------------  ----------------- */
	
	public static class GoPathField extends EnablementButtonTextField {
		
		public GoPathField() {
			super("GOPATH:", "Use same as GOPATH environment variable.", "Add Folder");
		}
		
		@Override
		protected String getDefaultFieldValue() throws CommonException {
			return ProcessUtils.getVarFromEnvMap(System.getenv(), "GOPATH");
		}
		
		@Override
		protected String getNewValueFromButtonSelection2() throws CommonException, OperationCancellation {
			String newValue = DirectoryTextField.openDirectoryDialog("", text.getShell());
			return getFieldValue() + File.pathSeparator + newValue;
		}
		
	}
	
}