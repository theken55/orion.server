/*******************************************************************************
 * Copyright (c) 2010, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.useradmin.servlets;

import java.io.IOException;
import java.util.Properties;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.authentication.IAuthenticationService;
import org.eclipse.orion.server.useradmin.UserAdminActivator;
import org.eclipse.orion.server.useradmin.UserServiceHelper;
import org.json.JSONException;
import org.osgi.service.http.HttpContext;
import org.osgi.service.useradmin.Authorization;
import org.osgi.service.useradmin.UserAdmin;

public class UserAuthFilter implements Filter {

	private static final String ADMIN_ROLE = "admin";

	private IAuthenticationService authenticationService;
	private Properties authProperties;
	
	private boolean everyoneCanCreateUsers = true;

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		authenticationService = UserAdminActivator.getDefault().getAuthenticationService();
		// treat lack of authentication as an error. Administrator should use
		// "None" to disable authentication entirely
		if (authenticationService == null) {
			String msg = "Authentication service is missing. The server configuration must specify an authentication scheme, or use \"None\" to indicate no authentication"; //$NON-NLS-1$
			LogHelper.log(new Status(IStatus.ERROR, UserAdminActivator.PI_USERADMIN, msg, null));
			throw new ServletException(msg);
		}
		
		IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode("org.eclipse.orion.server.configurator");
		everyoneCanCreateUsers = prefs.getBoolean("everyoneCanCreateUsers", true);
		
		// TODO need to read auth properties from InstanceScope preferences
		// authProperties =
		// ConfiguratorActivator.getDefault().getAuthProperties();
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		HttpServletRequest httpRequest = (HttpServletRequest) request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;

		// everyone can create a new user
		if (everyoneCanCreateUsers && "POST".equals(httpRequest.getMethod())) {
			chain.doFilter(request, response);
			return;
		}

		String login = authenticationService.authenticateUser(httpRequest, httpResponse, authProperties);
		if (login == null) {
			return;
		}

		request.setAttribute(HttpContext.REMOTE_USER, login);
		request.setAttribute(HttpContext.AUTHENTICATION_TYPE, authenticationService.getAuthType());

		UserAdmin userAdmin = UserServiceHelper.getDefault().getUserStore();
		Authorization authorization = userAdmin.getAuthorization(userAdmin.getUser("login", login));

		if (authorization.hasRole(ADMIN_ROLE)) {
			chain.doFilter(request, response);
			return;
		}

		try {
			if (!AuthorizationService.checkRights(login, httpRequest.getRequestURI().toString(), httpRequest.getMethod())) {
				httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
				return;
			}
		} catch (JSONException e) {
			httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return;
		}
		chain.doFilter(request, response);
	}

	@Override
	public void destroy() {
		// TODO Auto-generated method stub
	}
}
