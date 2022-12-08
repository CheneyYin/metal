import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import "reactflow/dist/style.css";
import { MetalNodeProps } from "./MetalView";
import { Alert, Divider, IconButton, LinearProgress, Paper, Skeleton, Stack } from "@mui/material";
import { MetalNodeEditor } from "./MetalNodeEditor";
import { MetalExplorer } from "./explorer/MetalExplorer";
import { Box } from "@mui/system";
import { MetalFlow } from "./MetalFlow";
import {
    ProjectProfile,
    ProjectProfileHandler,
    ProjectProfileViewer,
    ProjectProfileViewerHandler,
} from "../project/ProjectProfile";
import { VscExtensions, VscOpenPreview, VscSettingsGear } from "react-icons/vsc";
import { Project } from "../../model/Project";
import { designerId, MainHandler } from "../main/Main";
import { useAsync } from "../../api/Hooks";
import { State } from "../../api/State";
import { getProjectById } from "../../api/ProjectApi";
import { useAppSelector } from "../../app/hooks";
import { tokenSelector } from "../user/userSlice";
import { useSpecLoader } from "./SpecLoader";
import { ReactFlowProvider } from "reactflow";
import { useMetalFlow, useMetalNodeEditor } from "./DesignerProvider";
import { IReadOnly } from "../ui/Commons";
import { BackendPanel, BackendPanelHandler } from "./BackendPanel";

export interface DesignerProps extends IReadOnly {
    id: string;
    name?: string;
    mainHandler?: MainHandler;
}

export function Designer(props: DesignerProps) {
    const { id, mainHandler, isReadOnly } = props;
    const token: string | null = useAppSelector((state) => {
        return tokenSelector(state);
    });
    const [isOpenExplorer, setOpenExplorer] = useState(isReadOnly ? false : true);
    const [run, status, result, error] = useAsync<Project>();

    const project = result === null ? undefined : result;
    const specLoader = useSpecLoader(token, project?.spec);

    const projectProfileRef = useRef<ProjectProfileHandler>(null);
    const projectProfileViewerRef = useRef<ProjectProfileViewerHandler>(null);
    const backendPanelRef = useRef<BackendPanelHandler>(null);
    const metalFlowHandler = useMetalFlow();
    const nodeEditorHandler = useMetalNodeEditor();

    const isPending = () => status === State.pending;
    const isFailure = () => status === State.failure;

    const onSwitchExplorer = () => {
        if (isReadOnly !== undefined && isReadOnly === false) {
            return;
        }
        setOpenExplorer(!isOpenExplorer);
    };

    const rename = useCallback((newName: string) => {
        if (
            mainHandler !== undefined &&
            mainHandler.renameDesigner !== undefined
        ) {
            mainHandler.renameDesigner(designerId(id, isReadOnly), newName);
        }
    }, [id, isReadOnly, mainHandler]);

    const load = useCallback(() => {
        if (token !== null) {
            run(getProjectById(token, id)
            .then(proj => {
                rename(proj.name);
                return proj;
            }
            ));
        }
    }, [id, rename, run, token]);

    const onAddNode = useCallback(
        (nodeProps: MetalNodeProps) => {
            metalFlowHandler.addNode(nodeProps);
        },
        [metalFlowHandler]
    );

    const explorer = useMemo(() => {
        return <MetalExplorer addNode={onAddNode} restrictPkgs={project?.deploy.pkgs} />;
    }, [onAddNode, project?.deploy.pkgs]);

    const nodePropsWrap = useCallback(
        (nodeProps: MetalNodeProps) => ({
            ...nodeProps,
            editor: nodeEditorHandler,
        }),
        [nodeEditorHandler]
    );

    

    const progress = isPending() ? (
        <LinearProgress />
    ) : (
        <LinearProgress variant="determinate" value={0} />
    );

    const onReloadProject = (projectId: string) => {
        projectProfileRef.current?.close();
        if (mainHandler !== undefined) {
            if (mainHandler.close !== undefined) {
                mainHandler.close(designerId(id, isReadOnly));

                setTimeout(() => {
                    mainHandler.openDesigner({
                        id: id,
                        isReadOnly: isReadOnly,
                        mainHandler: mainHandler,
                    });
                }, 2000);
            }
        }
    };

    useEffect(() => {
        load();
    }, [load]);

    if (project === undefined) {
        return (
            <>
                {isPending() && progress}
                <Skeleton>
                    {isFailure() && <Alert severity={"error"}>{"Fail to load project."}</Alert>}
                </Skeleton>
            </>
        );
    }

    return (
        <div className="panel">
            {isPending() && progress}
            {isFailure() && <Alert severity={"error"}>{"Fail to load project."}</Alert>}
            {specLoader.status === State.failure && (
                <Alert severity={"error"}>{"Fail to load project spec."}</Alert>
            )}
            <Stack
                direction="row"
                justifyContent="center"
                alignItems="flex-start"
                spacing={2}
                sx={{ height: "100%", width: "100%" }}
            >
                <Box
                    component={Paper}
                    sx={{
                        height: "100%",
                        width: !isOpenExplorer ? "100%" : "75%",
                    }}
                >
                    <ReactFlowProvider>
                        <MetalFlow
                            isReadOnly={isReadOnly}
                            flow={specLoader.flow === null ? undefined : specLoader.flow}
                            nodePropsWrap={nodePropsWrap}
                        />
                    </ReactFlowProvider>
                </Box>
                {isOpenExplorer && (
                    <Box
                        component={Paper}
                        sx={{
                            height: "100%",
                            width: "25%",
                        }}
                    >
                        {explorer}
                    </Box>
                )}
            </Stack>
            <Paper
                elevation={2}
                sx={{
                    position: "absolute",
                    top: "1vh",
                    left: "1vw",
                    paddingTop: "1em",
                    paddingBottom: "1em",
                    paddingLeft: "1em",
                    paddingRight: "1em",
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "flex-start",
                    justifyContent: "flex-start",
                }}
            >
                <div
                    style={{
                        width: "100%",
                        display: "flex",
                        flexDirection: "row",
                        alignItems: "center",
                        justifyContent: "flex-start",
                    }}
                >
                    {!isReadOnly && (
                        <IconButton
                            onClick={() => {
                                if (projectProfileRef.current !== null) {
                                    projectProfileRef.current.open();
                                }
                            }}
                        >
                            <VscSettingsGear />
                        </IconButton>
                    )}

                    <IconButton
                        onClick={() => {
                            projectProfileViewerRef.current?.open(project);
                        }}
                    >
                        <VscOpenPreview />
                    </IconButton>

                    {!isReadOnly && (
                        <IconButton onClick={onSwitchExplorer}>
                            <VscExtensions />
                        </IconButton>
                    )}
                </div>
                <BackendPanel deployId={project.deploy.id} currentSpec={()=>{return metalFlowHandler.export()}} ref={backendPanelRef}/>
            </Paper>
            <MetalNodeEditor isReadOnly={isReadOnly} />
            <ProjectProfile
                open={false}
                isCreate={false}
                onFinish={onReloadProject}
                project={project}
                ref={projectProfileRef}
            />
            <ProjectProfileViewer ref={projectProfileViewerRef} />
        </div>
    );
}
