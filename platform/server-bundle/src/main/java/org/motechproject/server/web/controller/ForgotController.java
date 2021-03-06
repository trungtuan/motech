package org.motechproject.server.web.controller;

import org.motechproject.security.ex.UserNotFoundException;
import org.motechproject.security.ex.NonAdminUserException;
import org.motechproject.security.service.PasswordRecoveryService;
import org.motechproject.server.config.SettingsFacade;
import org.motechproject.server.config.domain.LoginMode;
import org.motechproject.server.startup.StartupManager;
import org.motechproject.server.web.dto.ForgotViewData;
import org.motechproject.server.web.helper.Header;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;

import javax.servlet.http.HttpServletRequest;

/**
 * Forgot Controller for reset password.
 */
@Controller
public class ForgotController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ForgotController.class);

    @Autowired
    private SettingsFacade settingsFacade;

    @Autowired
    private StartupManager startupManager;

    @Autowired
    private PasswordRecoveryService recoveryService;

    @Autowired
    private CookieLocaleResolver cookieLocaleResolver;

    @Autowired
    private BundleContext bundleContext;

    @RequestMapping(value = "/forgot", method = RequestMethod.GET)
    public ModelAndView login(final HttpServletRequest request) {
        if (startupManager.isBootstrapConfigRequired()) {
            return new ModelAndView(Constants.REDIRECT_BOOTSTRAP);
        }

        if (startupManager.isConfigRequired()) {
            return new ModelAndView(Constants.REDIRECT_STARTUP);
        }

        ModelAndView view = new ModelAndView("forgot");
        view.addObject("mainHeader", Header.generateHeader(bundleContext.getBundle()));
        view.addObject("error", request.getParameter("error"));

        return view;
    }

   @RequestMapping(value = "/forgotviewdata", method = RequestMethod.GET)
   @ResponseBody
   public ForgotViewData getForgotViewData(final HttpServletRequest request) {
        ForgotViewData view = new ForgotViewData();

        view.setLoginMode(settingsFacade.getPlatformSettings().getLoginMode());
        view.setEmailGetter(true);
        view.setProcessed(false);
        view.setEmail("");
        view.setPageLang(cookieLocaleResolver.resolveLocale(request));

        return view;
    }

    @RequestMapping(value = "/forgot", method = RequestMethod.POST)
    @ResponseBody
    public String forgotPost(@RequestBody String email) {

        LoginMode loginMode = settingsFacade.getPlatformSettings().getLoginMode();

        if (loginMode.isRepository()) {
            try {
                recoveryService.passwordRecoveryRequest(email);
            } catch (UserNotFoundException e) {
                LOGGER.debug("Request for a nonexistent email", e);
                return "security.forgot.noSuchUser";
            } catch (Exception e) {
                LOGGER.error("Error processing recovery", e);
                return "security.forgot.errorSending";
            }
        } else {
            try {
                recoveryService.oneTimeTokenOpenId(email);
            } catch (UserNotFoundException e) {
                LOGGER.debug("Request for a nonexistent email", e);
                return "security.forgot.noSuchUser";
            } catch (NonAdminUserException e) {
                LOGGER.debug("Request for a nonexistent email", e);
                return "security.forgot.nonAdminUser";
            } catch (Exception e) {
                LOGGER.error("Error processing recovery", e);
                return "security.forgot.errorSending";
            }
        }

        return null;
    }
}
