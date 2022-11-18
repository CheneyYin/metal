import {
    Alert,
    AppBar,
    Backdrop,
    Badge,
    Box,
    Button,
    Container,
    Divider,
    IconButton,
    LinearProgress,
    List,
    ListItem,
    Paper,
    Skeleton,
    Stack,
    Toolbar,
    Typography,
} from "@mui/material";
import { createTheme, ThemeProvider } from "@mui/material/styles";
import _, { filter } from "lodash";
import React, { useCallback, useEffect, useMemo, useState } from "react";
import { AiOutlineReload } from "react-icons/ai";
import { State } from "../../../api/State";
import { useAppSelector } from "../../../app/hooks";
import { metalType, MetalTypes } from "../../../model/Metal";
import { MetalPkg } from "../../../model/MetalPkg";
import { tokenSelector } from "../../user/userSlice";
import { metalViewIcon, MetalViewIcons } from "../MetalView";
import { getAllMetalPkgsOfUserAccess } from "./MetalPkgApi";

const theme = createTheme();

export interface MetalPkgProps {
    type: MetalTypes;
    metalPkg: MetalPkg;
}

export function MetalPkgView(props: MetalPkgProps) {
    const { type, metalPkg } = props;
    const classSubs = metalPkg.class.split(".");
    const className = classSubs.length > 0 ? classSubs[classSubs.length - 1] : "?";
    const pkgSubs = metalPkg.pkg.split(":");
    const groupId = pkgSubs.length > 0 ? pkgSubs[0] : "?";
    const artifactId = pkgSubs.length > 1 ? pkgSubs[1] : "?";
    const version = pkgSubs.length > 2 ? pkgSubs[2] : "?";

    return (
        <Stack direction="row" justifyContent="stretch" alignItems="center">
            {metalViewIcon(type)}
            <Stack
                direction="column"
                justifyContent="flex-start"
                alignItems="flex-start"
                spacing={2}
            >
                <Typography>{className}</Typography>
                <Stack direction="column" justifyContent="flex-start" alignItems="flex-start">
                    <Typography>{groupId}</Typography>
                    <Typography>{artifactId}</Typography>
                    <Typography>{version}</Typography>
                </Stack>
                <Stack>
                    <Button>{"Add"}</Button>
                </Stack>
            </Stack>
        </Stack>
    );
}

interface ITypeFilter {
    isOn: () => boolean;
    onToggle: () => void;
}

function TypeFilter(
    type: MetalTypes,
    pkgFilter: Set<MetalTypes>,
    setPkgFilter: React.Dispatch<React.SetStateAction<Set<MetalTypes>>>
): ITypeFilter {
    return {
        isOn: () => pkgFilter.has(type),
        onToggle: () => {
            if (pkgFilter.has(type)) {
                pkgFilter.delete(type);
            } else {
                pkgFilter.add(type);
            }
            setPkgFilter(_.clone(pkgFilter));
        },
    };
}

export function MetalExplorer() {
    const token: string | null = useAppSelector((state) => {
        return tokenSelector(state);
    });

    const [status, setStatus] = useState<State>(State.idle);
    const [metalPkgs, setMetalPkgs] = useState<MetalPkg[]>([]);
    const [pkgFilter, setPkgFilter] = useState<Set<MetalTypes>>(new Set<MetalTypes>());

    const isPending = () => status === State.pending;
    const isFailure = () => status === State.failure;

    const filters = useMemo(
        () => ({
            source: TypeFilter(MetalTypes.SOURCE, pkgFilter, setPkgFilter),
            sink: TypeFilter(MetalTypes.SINK, pkgFilter, setPkgFilter),
            mapper: TypeFilter(MetalTypes.MAPPER, pkgFilter, setPkgFilter),
            fusion: TypeFilter(MetalTypes.FUSION, pkgFilter, setPkgFilter),
            setup: TypeFilter(MetalTypes.SETUP, pkgFilter, setPkgFilter),
        }),
        [pkgFilter, setPkgFilter]
    );

    const load = useCallback(() => {
        if (token !== null) {
            setStatus(State.pending);
            getAllMetalPkgsOfUserAccess(token).then(
                (pkgs: MetalPkg[]) => {
                    setTimeout(() => {
                        setMetalPkgs(pkgs);
                        setStatus(State.success);
                    }, 3000);
                },
                (reason) => {
                    console.error(reason);
                    setStatus(State.failure);
                }
            );
        }
    }, [token]);

    useEffect(() => {
        load();
    }, [load]);

    // if (token === null || metalPkgs === null || metalPkgs.length === 0) {
    //     return <Skeleton />;
    // }

    const progress = isPending()? (
        <LinearProgress/>
    ): (
        <LinearProgress variant="determinate" value={0}/>
    )

    return (
        <ThemeProvider theme={theme}>
            <AppBar
                color="primary"
                position="sticky"
                sx={{ width: "auto", backgroundColor: "white" }}
            >
                <Stack
                    sx={{ width: "auto" }}
                    direction="row"
                    justifyContent="space-between"
                    alignItems="center"
                    divider={<Divider orientation="vertical" flexItem />}
                >
                    <Toolbar sx={{ width: "80%" }}>
                        <IconButton
                            disabled={isPending()}
                            color={filters.source.isOn() ? "primary" : "secondary"}
                            onClick={filters.source.onToggle}
                        >
                            {MetalViewIcons.SOURCE}
                        </IconButton>
                        <IconButton
                            disabled={isPending()}
                            color={filters.sink.isOn() ? "primary" : "secondary"}
                            onClick={filters.sink.onToggle}
                        >
                            {MetalViewIcons.SINK}
                        </IconButton>
                        <IconButton
                            disabled={isPending()}
                            color={filters.mapper.isOn() ? "primary" : "secondary"}
                            onClick={filters.mapper.onToggle}
                        >
                            {MetalViewIcons.MAPPER}
                        </IconButton>
                        <IconButton
                            disabled={isPending()}
                            color={filters.fusion.isOn() ? "primary" : "secondary"}
                            onClick={filters.fusion.onToggle}
                        >
                            {MetalViewIcons.FUSION}
                        </IconButton>
                        <IconButton
                            disabled={isPending()}
                            color={filters.setup.isOn() ? "primary" : "secondary"}
                            onClick={filters.setup.onToggle}
                        >
                            {MetalViewIcons.SETUP}
                        </IconButton>
                    </Toolbar>
                    <Toolbar sx={{ width: "20%" }}>
                        <IconButton disabled={isPending()} onClick={load}>
                            <AiOutlineReload />
                        </IconButton>
                    </Toolbar>
                </Stack>
            </AppBar>
            {progress}
            <Box
                component={Paper}
                sx={{ display: "flex", flexDirection:"column", height: "100%", width: "100%", overflow: "hidden" }}
            >
                <Stack>
                {isFailure() && <Alert severity={"error"}>{"Fail to load metal packages."}</Alert>}
                
                <List sx={{ overflowY: "scroll", width: "inherit" }}>
                    <Backdrop open={isPending()} sx={{ position: "absolute", height: "auto"}} />
                    {afterTypeFilter(pkgFilter, metalPkgs).map(
                        (metalPkg: MetalPkg, index: number) => {
                            const props = {
                                type: metalType(metalPkg.type),
                                metalPkg: metalPkg,
                            };
                            return (
                                <>
                                <ListItem key={index} sx={{ width: "auto" }}>
                                    <MetalPkgView {...props} />
                                </ListItem>
                                <Divider variant="inset" component="li" />
                                </>
                                
                            );
                        }
                    )}
                </List>
                </Stack>
            </Box>
        </ThemeProvider>
    );
}
function afterTypeFilter(pkgFilter: Set<MetalTypes>, pkgs: MetalPkg[]) {
    return pkgFilter.size > 0
        ? pkgs.filter((pkg: MetalPkg) => pkgFilter.has(metalType(pkg.type)))
        : pkgs;
}
